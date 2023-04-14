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
