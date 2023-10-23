# SweetRiceMC

服务器专用客户端启动器 (HMCL fork)

为了方便后来者为自己的服务器去改出一个稳定的、好看的启动器，以下是部分提示。

`org.jackhuang.hmcl.ui.main.MainPage` 中含有主界面的欢迎信息。其实光是改的测试版提示，自由度都挺高的，不知道为什么 HMCL 原版没加主页编辑。

`org.jaackhuang.hmcl.Metadata` 中有启动器名称、缓存目录、游戏协议地址等信息。其中我添加了 `EULA_URL_2` 为我服务器的玩家协议，将 hmcl 主要缓存目录从 `%appdata%/.hmcl` 改为了 `%appdata%/.hmcl-sweetrice`。推荐更改这个目录，这样才能在初次打开启动器时弹出同意协议的提示框。

`org.jackhuang.hmcl.ui.update` 包中的类是“更新客户端”的界面，`org.jackhuang.hmcl.util.GithubFileFetch` 是客户端更新的下载源，下载源使用 Gitee 加速，Github 兜底。

默认主题色请见 `org.jackhuang.hmcl.setting.Theme`，以及 assets 里的 css 文件 (.root 的 -fx-base-color)。图片资源都在 assets 里不解释。

关于页面的 `SweetRiceMC 客户端启动器` 标识在 `org.jackhuang.hmcl.ui.main.AboutPage`，其中也包含了我的版权信息和 HMCL 原作者黄鱼的版权信息，你可以增加你的版权信息或者修改我的版权信息 Subtitle，但不能删除。尽管这不是强制性的，是道德约束，我也要在这里提一下。

如需正版登录和 CurseForge 下载服务，请在编译时在参数中填写相关的 token。

`HMCLLauncher` 文件夹是启动器的 C++ 壳 (.exe)。它负责在 Windows 下寻找 java 并引导开启 HMCL，并且有一个跟正常 Windows 程序一样的图标和信息，显得美观一点。用 Visual Studio 打开编辑即可。
* `HMCL.rc` 第 100 行左右开始是 exe 的基本信息，也就是公司、版权、产品名、版本等信息。
* `HMCL.ico` 是图标
* 本仓库修改的 C++ 壳修改了相对位置 java 的路径。你可以把储存占用较小的 jre 放在 `./.minecraft/java` 文件夹里，确保文件 `./.minecraft/java/bin/javaw.exe` 存在，即可使用该 java，省去玩家下载 java 的步骤。原版 HMCL 也能做到，只是不能放 `.minecraft` 里不够优雅。

C++ 壳改完编译之后，要把编译出来的 exe 放到 `HMCL/src/main/resources/assets/HMCLauncher.exe`

## 注意

你可以使用这份源码为你的服务器制作一个启动器，保留“关于”页面中的版权信息、作出与原版 HMCL 不同区别标识即可，建议开源。

## 编译

```bash
gradlew build
```

编译结果在 `HMCL/build/libs`