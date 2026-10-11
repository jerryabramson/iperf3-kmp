package com.jabramson.iperf3_network_tester_kmp






import platform.Foundation.NSUUID
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.currentLocale
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication

import platform.UIKit.UIDevice


/**
 * Determines if the current device has a notch or cutout by evaluating
 * the key window's state within the application.
 *
 * @return `true` if the key window is determined to have a notch or cutout,
 *         `false` otherwise.
 */
fun hasNotch():  Boolean {
    val keyWindow: Boolean? = UIApplication.sharedApplication.keyWindow?.isKeyWindow()
    return keyWindow ?: false
}

/**
 * Represents an implementation of the Notch interface for iOS devices.
 * It determines whether the current iOS device has a notch or cutout
 * by using the `hasNotch` function.
 *
 * This class overrides the `hasNotch` property to provide platform-specific
 * behavior, returning a Boolean value depending on the device's configuration.
 */
class IOSNotch: Notch {
    override val hasNotch: Boolean = hasNotch()
}

/**
 * A platform-specific implementation of the `Platform` interface for iOS devices.
 *
 * This class retrieves and constructs the platform name utilizing the current device's
 * system name and system version. The `name` property provides a string representation
 * of the device's platform details.
 *
 * Example platform name format:
 * "iOS 16.5"
 *
 * The `IOSPlatform` class is typically used to obtain platform-specific details for
 * runtime environments in a shared codebase targeting multiple platforms.
 */
class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

/**
 * A platform-specific implementation of the `AppUUID` interface for iOS.
 *
 * This class generates a unique UUID using the `NSUUID` framework from the iOS platform.
 * The generated UUID is represented as a string and is accessible via the `name` property.
 *
 * The `IOSUUID` class provides a mechanism to obtain platform-dependent unique identifiers
 * that adhere to the `AppUUID` interface, ensuring compatibility in a shared codebase
 * targeting multiple platforms.
 */
class IOSUUID : AppUUID {
    override val name: String
        get() = NSUUID().UUIDString()
}

/**
 * Represents a platform-specific implementation of the `AppLocalDateTime` interface for iOS.
 *
 * This class provides a property `name` that returns the current date and time
 * as a formatted string specific to the iOS platform. The date and time format
 * conforms to the ISO 8601 standard in the form "yyyy-MM-dd'T'HH:mm:ss".
 *
 * The implementation uses `NSDateFormatter` to format the current date (`NSDate`)
 * based on the device's locale (`NSLocale.currentLocale`), ensuring the output is
 * consistent with the region and language settings of the device.
 *
 * Example output format:
 * - "2023-10-18T15:45:30"
 */
class IOSLocalDateTime: AppLocalDateTime {
    override val name: String
        get() {
            val formatter = NSDateFormatter()
            formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
            formatter.locale = NSLocale.currentLocale
            return formatter.stringFromDate(NSDate())
        }
}

/**
 * A platform-specific implementation of the `AppCurrentTimeMillis` interface for iOS.
 *
 * This class provides the current time in milliseconds since the Unix epoch
 * (January 1, 1970, 00:00:00 UTC) using the iOS `NSDate` framework. The time
 * is calculated by converting the `timeIntervalSince1970` value (which is in seconds)
 * to milliseconds.
 *
 * The `IOSCurrentTimeMillis` class ensures compatibility with platforms
 * that require a consistent and precise measure of time across the
 * shared codebase.
 */
class IOSCurrentTimeMillis : AppCurrentTimeMillis {
    override val value: Long
        get() = (NSDate().timeIntervalSince1970 * 1000).toLong()
}

// actual's
actual fun getNotch(): Notch = IOSNotch()
actual fun getPlatform(): Platform = IOSPlatform()
actual fun getAppUUID(): AppUUID = IOSUUID()
actual fun getAppLocalDateTime(): AppLocalDateTime = IOSLocalDateTime()
actual fun getAppCurrentTimeMillis(): AppCurrentTimeMillis = IOSCurrentTimeMillis()
