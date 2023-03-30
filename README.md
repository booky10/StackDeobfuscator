# Stack Deobfuscator

## Downloads

- Modrinth: https://modrinth.com/mod/stackdeobf
- Curseforge: https://curseforge.com/minecraft/mc-mods/stackdeobf

See [wiki](https://github.com/booky10/StackDeobfuscator/wiki/Configuration) for configuration

## What does this mod do?

All errors displayed in the console and all crash reports will be remapped from unreadable production names (e.g.
`net.minecraft.class_310`) to readable mapped names (e.g. `net.minecraft.client.MinecraftClient`).

This allows mod developers to more easily identify issues in a non-development environment, as the errors are instantly
human-readable.

### Comparison

<details>
<summary><b>Before</b></summary>

> ```
> [18:04:12] [Render thread/ERROR]: Reported exception thrown!
> net.minecraft.class_148: Manually triggered debug crash
>     at net.minecraft.class_309.method_1474(class_309.java:509) ~[client-intermediary.jar:?]
>     at net.minecraft.class_310.method_1574(class_310.java:1955) ~[client-intermediary.jar:?]
>     at net.minecraft.class_310.method_1523(class_310.java:1180) ~[client-intermediary.jar:?]
>     at net.minecraft.class_310.method_1514(class_310.java:801) ~[client-intermediary.jar:?]
>     at net.minecraft.client.main.Main.main(Main.java:237) ~[minecraft-1.19.4-client.jar:?]
>     at net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider.launch(MinecraftGameProvider.java:462) ~[fabric-loader-0.14.18.jar:?]
>     at net.fabricmc.loader.impl.launch.knot.Knot.launch(Knot.java:74) ~[fabric-loader-0.14.18.jar:?]
>     at net.fabricmc.loader.impl.launch.knot.KnotClient.main(KnotClient.java:23) ~[fabric-loader-0.14.18.jar:?]
>     at org.prismlauncher.launcher.impl.StandardLauncher.launch(StandardLauncher.java:88) ~[NewLaunch.jar:?]
>     at org.prismlauncher.EntryPoint.listen(EntryPoint.java:126) ~[NewLaunch.jar:?]
>     at org.prismlauncher.EntryPoint.main(EntryPoint.java:71) ~[NewLaunch.jar:?]
> Caused by: java.lang.Throwable: Manually triggered debug crash
>     at net.minecraft.class_309.method_1474(class_309.java:506) ~[client-intermediary.jar:?]
>     ... 10 more
> ```

</details>
<details>
<summary><b>After (yarn/quilt mappings)</b></summary>

> ```
> [18:02:00] [Render thread/ERROR]: Reported exception thrown!
> [18:02:00] [Render thread/ERROR]: net.minecraft.util.crash.CrashException: Manually triggered debug crash
>     at MC//net.minecraft.client.Keyboard.pollDebugCrash(Keyboard.java:509)
>     at MC//net.minecraft.client.MinecraftClient.tick(MinecraftClient.java:1955)
>     at MC//net.minecraft.client.MinecraftClient.render(MinecraftClient.java:1180)
>     at MC//net.minecraft.client.MinecraftClient.run(MinecraftClient.java:801)
>     at net.minecraft.client.main.Main.main(Main.java:237)
>     at net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider.launch(MinecraftGameProvider.java:462)
>     at net.fabricmc.loader.impl.launch.knot.Knot.launch(Knot.java:74)
>     at net.fabricmc.loader.impl.launch.knot.KnotClient.main(KnotClient.java:23)
>     at org.prismlauncher.launcher.impl.StandardLauncher.launch(StandardLauncher.java:88)
>     at org.prismlauncher.EntryPoint.listen(EntryPoint.java:126)
>     at org.prismlauncher.EntryPoint.main(EntryPoint.java:71)
> Caused by: java.lang.Throwable: Manually triggered debug crash
>     at MC//net.minecraft.client.Keyboard.pollDebugCrash(Keyboard.java:506)
>     ... 10 more
> ```

</details>
<details>
<summary><b>After (mojang mappings)</b></summary>

> ```
> [17:52:01] [Render thread/ERROR]: Reported exception thrown!
> [17:52:01] [Render thread/ERROR]: net.minecraft.ReportedException: Manually triggered debug crash
>     at MC//net.minecraft.client.KeyboardHandler.tick(KeyboardHandler.java:509)
>     at MC//net.minecraft.client.Minecraft.tick(Minecraft.java:1955)
>     at MC//net.minecraft.client.Minecraft.runTick(Minecraft.java:1180)
>     at MC//net.minecraft.client.Minecraft.run(Minecraft.java:801)
>     at net.minecraft.client.main.Main.main(Main.java:237)
>     at net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider.launch(MinecraftGameProvider.java:462)
>     at net.fabricmc.loader.impl.launch.knot.Knot.launch(Knot.java:74)
>     at net.fabricmc.loader.impl.launch.knot.KnotClient.main(KnotClient.java:23)
>     at org.prismlauncher.launcher.impl.StandardLauncher.launch(StandardLauncher.java:88)
>     at org.prismlauncher.EntryPoint.listen(EntryPoint.java:126)
>     at org.prismlauncher.EntryPoint.main(EntryPoint.java:71)
> Caused by: java.lang.Throwable: Manually triggered debug crash
>     at MC//net.minecraft.client.KeyboardHandler.tick(KeyboardHandler.java:506)
>     ... 10 more
> ```

</details>

## Mappings Overview

Mappings are downloaded and parsed asynchronously. They are downloaded only once per version.
Yarn and Quilt refresh their version every 48 hours to check for updates.

| Mappings | Compatible Minecraft Versions               | Download Size (zipped¹)²               | Cached Size (gzipped)²  |
|----------|---------------------------------------------|----------------------------------------|-------------------------|
| Yarn     | 18w49a (1.14 snapshot) or higher            | `1.2 MiB`                              | `1.2 MiB`               |
| Quilt    | 1.19.2 or higher                            | `1.2 MiB`                              | `1.2 MiB`               |
| Mojang   | 1.14.4 and 19w36a (1.15 snapshot) or higher | `7.5 MiB` (uncompressed) + `494.7 KiB` | `1.1 MiB` + `494.3 KiB` |

¹: Mojang mappings are not compressed<br>
²: Sizes as of 30th March 2023 (1.19.4 is latest)

## Building

```shell
./gradlew build # remove "./" on windows
```

The output jar can be found in `build` → `libs`.

## License

This project is licensed under [**LGPL-3.0-only**](./LICENSE) unless specified otherwise.
