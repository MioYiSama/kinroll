import Foundation
import Photos
import ImageIO
import UIKit
import UniformTypeIdentifiers
import Shared

final class PhotoKitBridgeImpl: NSObject, PhotoKitBridge {
    func authorizationStatus() -> Int32 {
        Int32(PHPhotoLibrary.authorizationStatus(for: .readWrite).rawValue)
    }

    func requestAuthorization(completion: @escaping @Sendable (KotlinInt) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
            DispatchQueue.main.async { completion(KotlinInt(int: Int32(status.rawValue))) }
        }
    }

    func fetchAssets() -> [PhotoAsset] {
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        let result = PHAsset.fetchAssets(with: .image, options: options)
        var assets = [PhotoAsset]()
        assets.reserveCapacity(result.count)
        result.enumerateObjects { asset, _, _ in
            let resource = PHAssetResource.assetResources(for: asset).first
            let mime = resource.flatMap { UTType($0.uniformTypeIdentifier)?.preferredMIMEType }
            assets.append(PhotoAsset(
                id: asset.localIdentifier, uri: asset.localIdentifier,
                fileName: resource?.originalFilename, mimeType: mime, sizeBytes: 0,
                width: Int32(asset.pixelWidth), height: Int32(asset.pixelHeight), orientation: 0,
                takenAt: Self.millis(asset.creationDate), addedAt: nil, modifiedAt: Self.millis(asset.modificationDate),
                album: nil, relativePath: nil, ownerPackage: nil,
                isFavorite: asset.isFavorite, isScreenshotHint: asset.mediaSubtypes.contains(.photoScreenshot),
                latitude: asset.location.map { KotlinDouble(double: $0.coordinate.latitude) },
                longitude: asset.location.map { KotlinDouble(double: $0.coordinate.longitude) }
            ))
        }
        return assets
    }

    func readDetails(id: String) -> PhotoDetails? {
        guard let asset = Self.asset(id) else { return nil }
        let options = PHImageRequestOptions()
        options.version = .original
        options.isNetworkAccessAllowed = false
        options.isSynchronous = true
        var details: PhotoDetails?
        PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, _, _, info in
            guard info?[PHImageCancelledKey] as? Bool != true,
                  let data, let source = CGImageSourceCreateWithData(data as CFData, nil),
                  let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any] else { return }
            let tiff = props[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [:]
            let exif = props[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
            let iso = (exif[kCGImagePropertyExifISOSpeedRatings as String] as? [NSNumber])?.first
            details = PhotoDetails(
                cameraMake: tiff[kCGImagePropertyTIFFMake as String] as? String,
                cameraModel: tiff[kCGImagePropertyTIFFModel as String] as? String,
                lensModel: exif[kCGImagePropertyExifLensModel as String] as? String,
                iso: iso.map { KotlinInt(int: $0.int32Value) },
                exposureTime: Self.double(exif[kCGImagePropertyExifExposureTime as String]),
                fNumber: Self.double(exif[kCGImagePropertyExifFNumber as String]),
                focalLength: Self.double(exif[kCGImagePropertyExifFocalLength as String]),
                latitude: asset.location.map { KotlinDouble(double: $0.coordinate.latitude) },
                longitude: asset.location.map { KotlinDouble(double: $0.coordinate.longitude) },
                sizeBytes: KotlinLong(longLong: Int64(data.count))
            )
        }
        return details
    }

    func loadPixels(id: String, maxSide: Int32) -> BridgePixels? {
        guard maxSide > 0, let asset = Self.asset(id), asset.pixelWidth > 0, asset.pixelHeight > 0 else { return nil }
        let scale = min(1, Double(maxSide) / Double(max(asset.pixelWidth, asset.pixelHeight)))
        let width = max(1, Int((Double(asset.pixelWidth) * scale).rounded()))
        let height = max(1, Int((Double(asset.pixelHeight) * scale).rounded()))
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .exact
        options.isSynchronous = true
        options.isNetworkAccessAllowed = false
        var result: BridgePixels?
        PHImageManager.default().requestImage(for: asset, targetSize: CGSize(width: width, height: height),
                                              contentMode: .aspectFit, options: options) { image, info in
            guard info?[PHImageCancelledKey] as? Bool != true,
                  info?[PHImageResultIsDegradedKey] as? Bool != true, let image else { return }
            var rgba = Data(count: width * height * 4)
            let rendered = rgba.withUnsafeMutableBytes { raw -> Bool in
                guard let address = raw.baseAddress,
                      let space = CGColorSpace(name: CGColorSpace.sRGB),
                      let context = CGContext(data: address, width: width, height: height, bitsPerComponent: 8,
                                              bytesPerRow: width * 4, space: space,
                                              bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.noneSkipLast.rawValue) else { return false }
                context.setFillColor(UIColor.white.cgColor)
                context.fill(CGRect(x: 0, y: 0, width: width, height: height))
                // UIKit drawing applies UIImage.imageOrientation, including mirrored EXIF variants.
                context.translateBy(x: 0, y: CGFloat(height))
                context.scaleBy(x: 1, y: -1)
                UIGraphicsPushContext(context)
                image.draw(in: CGRect(x: 0, y: 0, width: width, height: height))
                UIGraphicsPopContext()
                let bytes = raw.bindMemory(to: UInt8.self)
                for offset in stride(from: 3, to: bytes.count, by: 4) { bytes[offset] = 255 }
                return true
            }
            if rendered { result = BridgePixels(width: Int32(width), height: Int32(height), rgba: rgba) }
        }
        return result
    }

    func deleteAssets(ids: [String], completion: @escaping @Sendable (KotlinBoolean) -> Void) {
        PHPhotoLibrary.shared().performChanges {
            PHAssetChangeRequest.deleteAssets(PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil))
        } completionHandler: { success, error in
            if let error { print("Photo deletion failed: \(error)") }
            DispatchQueue.main.async { completion(KotlinBoolean(bool: success)) }
        }
    }

    private static func asset(_ id: String) -> PHAsset? {
        PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject
    }
    private static func millis(_ date: Date?) -> KotlinLong? {
        date.map { KotlinLong(longLong: Int64($0.timeIntervalSince1970 * 1000)) }
    }
    private static func double(_ number: Any?) -> KotlinDouble? {
        (number as? NSNumber).map { KotlinDouble(double: $0.doubleValue) }
    }
}
