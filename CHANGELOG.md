## v1.3.0

- Mappings are now loaded asynchronously, removing startup time impact
- Added more log messages (e.g. time tracking and detailed http requests)
- Added support for (mostly) every minecraft version (starting from 18w49a)
    - 18w49 because this is the first yarn release
    - Mojang mappings will error if used below 19w36a (1.14.4 excluded)
    - Quilt mappings will error if used below 1.19.2
- Yarn/Quilt versions are now cached for two days before being refreshed

## v1.2.1

- Fixed some issues with remapping of inner classes
- Added support for quilt mappings

## v1.2.0

- Added support for yarn and custom mappings
    - Yarn mappings are now selected by default
    - See [wiki](https://github.com/booky10/StackDeobfuscator/wiki) on how to configure other mappings

## v1.1.0

- Initial public release
