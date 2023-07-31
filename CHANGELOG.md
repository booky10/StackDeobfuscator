## v1.4.0

### Fixes

- Handle `/` being used instead of `.` as package separator
    - E.g. `net/minecraft/class_5272` is now remapped correctly
      to `net/minecraft/client/item/ModelPredicateProviderRegistry`
- Fix some classes causing errors when trying to remap (
  Fixes [#7](https://github.com/booky10/StackDeobfuscator/issues/7))

### New

- Separate fabric integration and common remapping code
    - Adds web subproject for remapping text to different mappings/versions
- The checksum of all files is now verified on download
    - Intermediary/Quilt mappings always use sha512
    - Mojang mappings always use md5/sha1
    - Yarn uses sha512 on modern version, but falls back to sha1 for 19w02a, Combat Test 4 and all versions older than
      1.15 (except 1.14.4)
- Some fixes to support more versions
- Mapping/Metadata downloads will be retried 3x before being cancelled, if:
    - The server returns a non 2xx response code
    - Checksum verification fails

## v1.3.2

- Fix exceptions when remapping lambda methods
- Added optional remapping of every log message
- Rewrote internal log injection handling
    - Now uses Log4j's exception rendering
    - Removed "MC//" prefix in stacktrace, minecraft classes are now suffixed with "`~[client-intermediary.jar:?]`"

## v1.3.1

- Added support for quilt mappings below 1.19.2
    - Now goes down to 1.18.2

## v1.3.0

- Mappings are now loaded asynchronously, removing startup time impact
- Added more log messages (e.g. time tracking and detailed http requests)
- Added support for more minecraft versions
    - Yarn: 18w49a (1.14 snapshot) or higher
    - Quilt: 1.19.2 or higher
    - Mojang: 1.14.4 and 19w36a (1.15 snapshot) or higher
- Yarn/Quilt versions are now cached for 48 hours before being refreshed
- Added note in stacktraces when something has been remapped (`MC//` prefix before classname)
- Custom mappings now support the in-jar format used by intermediary, yarn and quilt
    - They also support GZIP (without TAR) and normal ZIP (just one file in a zip) compression<br>
      → Auto-detected by file name extension
- All cached mappings (yarn, quilt, intermediary and mojang) are now saved GZIP compressed

## v1.2.1

- Fixed some issues with remapping of inner classes
- Added support for quilt mappings

## v1.2.0

- Added support for yarn and custom mappings
    - Yarn mappings are now selected by default
    - See [wiki](https://github.com/booky10/StackDeobfuscator/wiki) on how to configure other mappings

## v1.1.0

- Initial public release
