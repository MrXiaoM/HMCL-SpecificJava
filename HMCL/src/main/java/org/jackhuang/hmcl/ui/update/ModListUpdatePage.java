/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.update.ModListUpdatePageSkin.ModInfoObject;
import org.jackhuang.hmcl.ui.versions.ModCheckUpdatesTask;
import org.jackhuang.hmcl.ui.versions.ModUpdatesPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.util.GithubFileFetch;
import org.jackhuang.hmcl.util.TaskCancellationAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModListUpdatePage extends ListPageBase<ModListUpdatePageSkin.ModInfoObject> implements VersionPage.VersionLoadable, PageAware {
    private final BooleanProperty modded = new SimpleBooleanProperty(this, "modded", false);

    private ModManager modManager;
    private LibraryAnalyzer libraryAnalyzer;
    private Profile profile;
    private String versionId;
    private List<GithubFileFetch.ModInfo> remoteMods = new ArrayList<>();
    public ModListUpdatePage(Profile profile, String id) {
        loadVersion(profile, id);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModListUpdatePageSkin(this);
    }

    @Override
    public void loadVersion(Profile profile, String id) {
        this.profile = profile;
        this.versionId = id;

        libraryAnalyzer = LibraryAnalyzer.analyze(profile.getRepository().getResolvedPreservingPatchesVersion(id));
        modded.set(libraryAnalyzer.hasModLoader());
        modManager = profile.getRepository().getModManager(id);
        checkUpdates();
    }

    private CompletableFuture<?> loadMods(ModManager modManager) {
        this.modManager = modManager;
        return CompletableFuture.supplyAsync(() -> {
            try {
                synchronized (ModListUpdatePage.this) {
                    runInFX(() -> loadingProperty().set(true));
                    modManager.refreshMods();
                    return new ArrayList<>(modManager.getMods());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Schedulers.defaultScheduler()).whenCompleteAsync((list, exception) -> {
            loadingProperty().set(false);
            if (exception == null) {
                // 过滤出更新列表里的mod
                List<ModInfoObject> items = remoteMods.stream()
                        .map(it -> {
                            for (LocalModFile mod : list) {
                                if (it.isSameMod(mod)) return new ModInfoObject(mod, it);
                            }
                            return new ModInfoObject(it);
                        })
                        .collect(Collectors.toList());
                // 添加更新列表外的mod
                items.addAll(list.stream()
                        .filter(mod -> remoteMods.stream().noneMatch(it -> it.isSameMod(mod)))
                        .map(ModInfoObject::new)
                        .collect(Collectors.toList()));

                Collections.sort(items);
                itemsProperty().setAll(items);
            }
            else {
                getProperties().remove(ModListUpdatePage.class);
            }
            // https://github.com/huanghongxun/HMCL/issues/938
            System.gc();
        }, Platform::runLater);
    }

    public void removeSelected(ObservableList<ModListUpdatePageSkin.ModInfoObject> selectedItems) {
        try {
            modManager.removeMods(selectedItems.stream()
                    .filter(Objects::nonNull)
                    .map(ModListUpdatePageSkin.ModInfoObject::getModInfo)
                    .toArray(LocalModFile[]::new));
            loadMods(modManager);
        } catch (IOException ignore) {
            // Fail to remove mods if the game is running or the mod is absent.
        }
    }

    public void enableSelected(ObservableList<ModListUpdatePageSkin.ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModListUpdatePageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(true));
    }

    public void disableSelected(ObservableList<ModListUpdatePageSkin.ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModListUpdatePageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(false));
    }

    public void openModFolder() {
        FXUtils.openFolder(new File(profile.getRepository().getRunDirectory(versionId), "mods"));
    }

    public void checkUpdates() {
        CompletableFuture.runAsync(() -> {
            remoteMods = GithubFileFetch.fetchUpdate();
        }, Schedulers.defaultScheduler()).whenComplete((ignored, ex) -> {
            loadMods(modManager);
        });
    }

    public void download() {
        // TODO 更新选中mod
        List<ModInfoObject> items = getItems().stream()
                .filter(ModInfoObject::isSelected)
                .filter(ModInfoObject::canUpdate)
                .collect(Collectors.toList());
        Controllers.taskDialog(
                Task.composeAsync(() -> {
                            Optional<String> gameVersion = profile.getRepository().getGameVersion(versionId);
                            return gameVersion.map(s -> new ModDownloadUpdatesTask(s, items)).orElse(null);
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception != null) {
                                Controllers.dialog("Failed to check updates", "failed", MessageDialogPane.MessageType.ERROR);
                            } else if (result.isEmpty()) {
                                Controllers.dialog(i18n("mods.check_updates.empty") + items.stream().map(it->it.getUpdateInfo().modId).collect(Collectors.joining(", ")));
                            } else {
                                List<String> failedMods = result.stream().filter(it -> it.startsWith("!")).map(it -> it.substring(1)).collect(Collectors.toList());
                                result.removeIf(it -> it.startsWith("!"));
                                StringBuilder sb = new StringBuilder("更新执行完毕!");
                                if (!result.isEmpty()) {
                                    sb.append("\n\n更新成功: (").append(result.size()).append(" 个)\n").append(String.join(", ", result));
                                }
                                if (!failedMods.isEmpty()) {
                                    sb.append("\n\n更新失败: (").append(failedMods.size()).append(" 个)\n").append(String.join(", ", failedMods));
                                }
                                Controllers.dialog(sb.toString());
                            }
                        })
                        .withStagesHint(Collections.singletonList("正在更新 Mods…")),
                "更新客户端Mod (" + items.size() + " 个)", TaskCancellationAction.NORMAL);

    }

    public void rollback(LocalModFile from, LocalModFile to) {
        try {
            modManager.rollback(from, to);
            loadMods(modManager);
        } catch (IOException ex) {
            Controllers.showToast(i18n("message.failed"));
        }
    }

    public boolean isModded() {
        return modded.get();
    }

    public BooleanProperty moddedProperty() {
        return modded;
    }

    public void setModded(boolean modded) {
        this.modded.set(modded);
    }
}
