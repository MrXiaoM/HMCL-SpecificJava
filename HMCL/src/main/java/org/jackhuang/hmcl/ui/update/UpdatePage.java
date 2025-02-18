/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.update;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import jdk.internal.loader.FileURLMapper;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.InstallersPage;
import org.jackhuang.hmcl.ui.download.UpdateInstallerWizardProvider;
import org.jackhuang.hmcl.ui.download.VersionsPage;
import org.jackhuang.hmcl.ui.versions.*;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.ResourcePackUpdater;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class UpdatePage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle("更新客户端", -1));
    private final TabHeader tab;
    private final TabHeader.Tab<ModListUpdatePage> modTab = new TabHeader.Tab<>("modTab");
    private final TransitionPane transitionPane = new TransitionPane();
    private final String versionId = "SweetRice";
    private WeakListenerHolder listenerHolder;

    public UpdatePage() {
        modTab.setNodeSupplier(() -> new ModListUpdatePage(Profiles.getSelectedProfile(),versionId));
        tab = new TabHeader(modTab);

        Profiles.registerVersionsListener(this::loadVersions);

        tab.select(modTab);
        FXUtils.onChangeAndOperate(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE.getAnimationProducer());
        });

        {
            AdvancedListBox sideBar = new AdvancedListBox()
                    .startCategory(i18n("download.content"))
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("mods"));
                        item.setLeftGraphic(wrap(SVG.PUZZLE));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(modTab));
                        item.setOnAction(e -> tab.select(modTab));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("resourcepack"));
                        item.setLeftGraphic(wrap(SVG.TEXTURE_BOX));
                        item.activeProperty().set(false);
                        item.setOnAction(e -> updateResourcePack());
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            setLeft(sideBar);
        }

        setCenter(transitionPane);
    }

    private void updateResourcePack() {
        Controllers.taskDialog(
                resourceUpdateTask(versionId).whenComplete(Schedulers.javafx(), exception -> {
                    if (exception != null) {
                        if (exception instanceof CancellationException) {
                            Controllers.showToast(i18n("message.cancelled"));
                        } else {
                            Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                        }
                    } else {
                        if (resourceCheckFail) {
                            resourceCheckFail = false;
                            Controllers.showToast(i18n("install.failed.downloading"));
                        } else if (resourceLatest) {
                            resourceLatest = false;
                            Controllers.showToast(i18n("update.latest"));
                        } else {
                            Controllers.showToast(i18n("install.success"));
                        }
                    }
                }), i18n("message.downloading"), TaskCancellationAction.NORMAL);
    }
    private static boolean resourceCheckFail = false;
    private static boolean resourceLatest = false;
    public static Task<Void> resourceUpdateTask(String versionId) {
        resourceCheckFail = false;
        resourceLatest = false;
        if (!ResourcePackUpdater.enable) return Task.runAsync(() -> {});
        File mcDir = Profiles.getSelectedProfile().getRepository().getRunDirectory(versionId);
        File resFile = ResourcePackUpdater.getResourcePackFile(mcDir);
        File shaFile = ResourcePackUpdater.getResourcePackSHA1File(mcDir);
        return Task.composeAsync(() -> {
            String sha1 = ResourcePackUpdater.getSHA1FromApi();
            if (sha1 == null) {
                resourceCheckFail = true;
                return null;
            }
            String localSha1 = shaFile.exists() ? FileUtils.readText(shaFile) : "";
            if (localSha1.equals(sha1)) {
                resourceLatest = true;
                return null;
            }
            FileUtils.writeText(shaFile, sha1);

            FileDownloadTask task = new FileDownloadTask(ResourcePackUpdater.getAllDownloadLinks(), resFile);
            task.setName("服务器材质包");
            return task;
        });
    }

    private static void download(Profile profile, @Nullable String version, RemoteMod.Version file, String subdirectoryName) {
        if (version == null) version = profile.getSelectedVersion();

        Path runDirectory = profile.getRepository().hasVersion(version) ? profile.getRepository().getRunDirectory(version).toPath() : profile.getRepository().getBaseDirectory().toPath();

        Controllers.prompt(i18n("archive.name"), (result, resolve, reject) -> {
            if (!OperatingSystem.isNameValid(result)) {
                reject.accept(i18n("install.new_game.malformed"));
                return;
            }
            Path dest = runDirectory.resolve(subdirectoryName).resolve(result);

            Controllers.taskDialog(Task.composeAsync(() -> {
                FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(file.getFile().getUrl()), dest.toFile());
                task.setName(file.getName());
                return task;
            }).whenComplete(Schedulers.javafx(), exception -> {
                if (exception != null) {
                    if (exception instanceof CancellationException) {
                        Controllers.showToast(i18n("message.cancelled"));
                    } else {
                        Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                    }
                } else {
                    Controllers.showToast(i18n("install.success"));
                }
            }), i18n("message.downloading"), TaskCancellationAction.NORMAL);

            resolve.run();
        }, file.getFile().getFilename());

    }

    private void loadVersions(Profile profile) {
        listenerHolder = new WeakListenerHolder();
        runInFX(() -> {
            if (profile == Profiles.getSelectedProfile()) {
                listenerHolder.add(FXUtils.onWeakChangeAndOperate(profile.selectedVersionProperty(), version -> {
                    if (modTab.isInitialized()) {
                        modTab.getNode().loadVersion(profile, null);
                    }
                }));
            }
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public void showModDownloads() {
        tab.select(modTab);
    }

    private static final class DownloadNavigator implements Navigation {
        private final Map<String, Object> settings = new HashMap<>();

        @Override
        public void onStart() {

        }

        @Override
        public void onNext() {

        }

        @Override
        public void onPrev(boolean cleanUp) {
        }

        @Override
        public boolean canPrev() {
            return false;
        }

        @Override
        public void onFinish() {

        }

        @Override
        public void onEnd() {

        }

        @Override
        public void onCancel() {

        }

        @Override
        public Map<String, Object> getSettings() {
            return settings;
        }

        public void onGameSelected() {
            Profile profile = Profiles.getSelectedProfile();
            if (profile.getRepository().isLoaded()) {
                Controllers.getDecorator().startWizard(new VanillaInstallWizardProvider(profile, (GameRemoteVersion) settings.get("game")), i18n("install.new_game"));
            }
        }

    }

    private static class VanillaInstallWizardProvider implements WizardProvider {
        private final Profile profile;
        private final DefaultDependencyManager dependencyManager;
        private final DownloadProvider downloadProvider;
        private final GameRemoteVersion gameVersion;

        public VanillaInstallWizardProvider(Profile profile, GameRemoteVersion gameVersion) {
            this.profile = profile;
            this.gameVersion = gameVersion;
            this.downloadProvider = DownloadProviders.getDownloadProvider();
            this.dependencyManager = profile.getDependency(downloadProvider);
        }

        @Override
        public void start(Map<String, Object> settings) {
            settings.put(PROFILE, profile);
            settings.put(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId(), gameVersion);
        }

        private Task<Void> finishVersionDownloadingAsync(Map<String, Object> settings) {
            GameBuilder builder = dependencyManager.gameBuilder();

            String name = (String) settings.get("name");
            builder.name(name);
            builder.gameVersion(((RemoteVersion) settings.get(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId())).getGameVersion());

            for (Map.Entry<String, Object> entry : settings.entrySet())
                if (!LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId().equals(entry.getKey()) && entry.getValue() instanceof RemoteVersion)
                    builder.version((RemoteVersion) entry.getValue());

            return builder.buildAsync().whenComplete(any -> profile.getRepository().refreshVersions())
                    .thenRunAsync(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
        }

        @Override
        public Object finish(Map<String, Object> settings) {
            settings.put("title", i18n("install.new_game"));
            settings.put("success_message", i18n("install.success"));
            settings.put("failure_callback", (FailureCallback) (settings1, exception, next) -> UpdateInstallerWizardProvider.alertFailureMessage(exception, next));

            return finishVersionDownloadingAsync(settings);
        }

        @Override
        public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
            switch (step) {
                case 0:
                    return new InstallersPage(controller, profile.getRepository(), ((RemoteVersion) controller.getSettings().get("game")).getGameVersion(), downloadProvider);
                default:
                    throw new IllegalStateException("error step " + step + ", settings: " + settings + ", pages: " + controller.getPages());
            }
        }

        @Override
        public boolean cancel() {
            return true;
        }

        public static final String PROFILE = "PROFILE";
    }
}
