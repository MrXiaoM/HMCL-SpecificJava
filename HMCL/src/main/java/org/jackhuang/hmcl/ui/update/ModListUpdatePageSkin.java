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

import com.jfoenix.controls.*;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.versions.ModTranslations;
import org.jackhuang.hmcl.util.GithubFileFetch;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.isNotBlank;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class ModListUpdatePageSkin extends SkinBase<ModListUpdatePage> {

    private final TransitionPane toolbarPane;
    private final HBox searchBar;
    private final HBox toolbarNormal;

    private final JFXListView<ModInfoObject> listView;
    private final JFXTextField searchField;

    // FXThread
    private boolean isSearching = false;

    ModListUpdatePageSkin(ModListUpdatePage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        listView = new JFXListView<>();

        {
            toolbarPane = new TransitionPane();

            searchBar = new HBox();
            toolbarNormal = new HBox();

            // Search Bar
            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchField.setOnAction(e -> search());

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    () -> {
                        changeToolbar(toolbarNormal);

                        isSearching = false;
                        searchField.clear();
                        Bindings.bindContent(listView.getItems(), getSkinnable().getItems());
                    });

            searchBar.getChildren().setAll(searchField, closeSearchBar);

            // Toolbar Normal
            toolbarNormal.getChildren().setAll(
                    createToolbarButton2(i18n("mods.check_updates"), SVG.UPDATE, skinnable::checkUpdates),
                    createToolbarButton2("更新选中模组", SVG.DOWNLOAD_OUTLINE, skinnable::download),
                    createToolbarButton2(i18n("folder.mod"), SVG.FOLDER_OPEN, skinnable::openModFolder),
                    createToolbarButton2(i18n("search"), SVG.MAGNIFY, () -> changeToolbar(searchBar))
            );

            FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                    selectedItem -> {
                        if (selectedItem != null) {
                            listView.getSelectionModel().clearSelection();
                        }
                        changeToolbar(isSearching ? searchBar : toolbarNormal);
                    });
            root.getContent().add(toolbarPane);
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            Holder<Object> lastCell = new Holder<>();
            listView.setCellFactory(x -> new ModInfoListCell(listView, lastCell));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getItems());

            center.setContent(listView);
            root.getContent().add(center);
        }

        Label label = new Label(i18n("mods.not_modded"));
        label.prefWidthProperty().bind(pane.widthProperty().add(-100));

        FXUtils.onChangeAndOperate(skinnable.moddedProperty(), modded -> {
            if (modded) pane.getChildren().setAll(root);
            else pane.getChildren().setAll(label);
        });

        getChildren().setAll(pane);
    }

    private void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE.getAnimationProducer());
        }
    }

    private void search() {
        isSearching = true;

        Bindings.unbindContent(listView.getItems(), getSkinnable().getItems());

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            listView.getItems().setAll(getSkinnable().getItems());
        } else {
            listView.getItems().clear();

            Predicate<String> predicate;
            if (queryString.startsWith("regex:")) {
                try {
                    Pattern pattern = Pattern.compile(queryString.substring("regex:".length()));
                    predicate = s -> pattern.matcher(s).find();
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "Illegal regular expression", e);
                    return;
                }
            } else {
                String lowerQueryString = queryString.toLowerCase(Locale.ROOT);
                predicate = s -> s.toLowerCase(Locale.ROOT).contains(lowerQueryString);
            }

            // Do we need to search in the background thread?
            for (ModInfoObject item : getSkinnable().getItems()) {
                String targetName = item.getModInfo() != null ? item.getModInfo().getFileName() : item.info.fileName;
                if (predicate.test(targetName)) {
                    listView.getItems().add(item);
                }
            }
        }
    }

    static class ModInfoObject extends RecursiveTreeObject<ModInfoObject> implements Comparable<ModInfoObject> {

        private final LocalModFile localModFile;
        private final String message;
        private final ModTranslations.Mod mod;
        private final GithubFileFetch.ModInfo info;
        private boolean update = false;
        private BooleanProperty selected = new SimpleBooleanProperty(true);
        ModInfoObject(LocalModFile localModFile) {
            this(localModFile, null);
        }
        ModInfoObject(LocalModFile localModFile, GithubFileFetch.ModInfo info) {
            this.localModFile = localModFile;
            this.info = info;
            StringBuilder message = new StringBuilder(localModFile.getName());
            if (info == null) {
                message.insert(0, "【无需更新】 ");
            } else {
                update = !info.hash.equalsIgnoreCase(GithubFileFetch.calcHash(localModFile.getFile().toFile().getAbsolutePath()));
                if (update) {
                    message.insert(0, "【可更新】 ");
                } else {
                    message.insert(0, "【已是最新】 ");
                }
            }
            if (isNotBlank(localModFile.getVersion()))
                message.append(", ").append(i18n("archive.version")).append(": ").append(localModFile.getVersion());
            if (isNotBlank(localModFile.getGameVersion()))
                message.append(", ").append(i18n("archive.game_version")).append(": ").append(localModFile.getGameVersion());
            if (isNotBlank(localModFile.getAuthors()))
                message.append(", ").append(i18n("archive.author")).append(": ").append(localModFile.getAuthors());
            this.message = message.toString();
            this.mod = ModTranslations.MOD.getModById(localModFile.getId());
        }

        ModInfoObject(GithubFileFetch.ModInfo info) {
            this.localModFile = null;
            this.info = info;
            this.update = true;
            this.message = "【可安装】";
            this.mod = ModTranslations.MOD.getModById(info.modId);
        }

        String getTitle() {
            return localModFile == null ? info.fileName : localModFile.getFileName();
        }

        String getSubtitle() {
            return message;
        }

        LocalModFile getModInfo() {
            return localModFile;
        }

        GithubFileFetch.ModInfo getUpdateInfo() {
            return info;
        }

        public ModTranslations.Mod getMod() {
            return mod;
        }
        public boolean canUpdate() {
            return update;
        }
        public boolean isSelected() {
            return selected.get();
        }
        @Override
        public int compareTo(@NotNull ModListUpdatePageSkin.ModInfoObject o) {
            if (o.info != null && info == null) return 1;
            if (o.info == null && info != null) return -1;
            if (o.localModFile == null && localModFile != null) return 1;
            if (o.localModFile != null && localModFile == null) return -1;
            if (o.update && !update) return 1;
            if (!o.update && update) return -1;
            return getTitle().toLowerCase().compareTo(o.getTitle().toLowerCase());
        }
    }

    private static final Lazy<PopupMenu> menu = new Lazy<>(PopupMenu::new);
    private static final Lazy<JFXPopup> popup = new Lazy<>(() -> new JFXPopup(menu.get()));
    static class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);
            boolean hasLocal = modInfo.getModInfo() != null;

            ImageView imageView = new ImageView();
            if (hasLocal) {
                if (StringUtils.isNotBlank(modInfo.getModInfo().getLogoPath())) {
                    Task.supplyAsync(() -> {
                        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modInfo.getModInfo().getFile())) {
                            Path iconPath = fs.getPath(modInfo.getModInfo().getLogoPath());
                            if (Files.exists(iconPath)) {
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                Files.copy(iconPath, stream);
                                return new ByteArrayInputStream(stream.toByteArray());
                            }
                        }
                        return null;
                    }).whenComplete(Schedulers.javafx(), (stream, exception) -> {
                        if (stream != null) {
                            imageView.setImage(new Image(stream, 40, 40, true, true));
                        } else {
                            imageView.setImage(new Image("/assets/img/command.png", 40, 40, true, true));
                        }
                    }).start();
                }
            }
            TwoLineListItem title = new TwoLineListItem();
            if (hasLocal) {
                title.setTitle(modInfo.getModInfo().getName());
                if (modInfo.canUpdate()) {
                    title.getTags().add("可更新");
                    title.setStyle(title.getStyle() + ";-fx-text-fill: #FF6A00;");
                }
                if (StringUtils.isNotBlank(modInfo.getModInfo().getVersion())) {
                    title.getTags().addAll(modInfo.getModInfo().getVersion());
                }
                title.setSubtitle(FileUtils.getName(modInfo.getModInfo().getFile()));
                titleContainer.getChildren().add(FXUtils.limitingSize(imageView, 40, 40));
            } else {
                title.setTitle(modInfo.getUpdateInfo().modId);
                title.setSubtitle(modInfo.getUpdateInfo().fileName);
                title.getTags().add("可安装");
                if (modInfo.getMod() != null && StringUtils.isNotBlank(modInfo.getMod().getDisplayName())) {
                    title.getTags().add(modInfo.getMod().getDisplayName());
                }
            }
            titleContainer.getChildren().add(title);
            setHeading(titleContainer);
            if (hasLocal) {
                Label description = new Label(modInfo.getModInfo().getDescription().toString());
                setBody(description);

                if (StringUtils.isNotBlank(modInfo.getModInfo().getUrl())) {
                    JFXHyperlink officialPageButton = new JFXHyperlink(i18n("mods.url"));
                    officialPageButton.setOnAction(e -> {
                        fireEvent(new DialogCloseEvent());
                        FXUtils.openLink(modInfo.getModInfo().getUrl());
                    });

                    getActions().add(officialPageButton);
                }
            }
            if (modInfo.getMod() != null && StringUtils.isNotBlank(modInfo.getMod().getMcbbs())) {
                JFXHyperlink mcbbsButton = new JFXHyperlink(i18n("mods.mcbbs"));
                mcbbsButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(ModManager.getMcbbsUrl(modInfo.getMod().getMcbbs()));
                });
                getActions().add(mcbbsButton);
            }

            if (modInfo.getMod() == null || StringUtils.isBlank(modInfo.getMod().getMcmod())) {
                JFXHyperlink searchButton = new JFXHyperlink(i18n("mods.mcmod.search"));
                searchButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                            pair("key", hasLocal ? modInfo.getModInfo().getName() : modInfo.getUpdateInfo().modId),
                            pair("site", "all"),
                            pair("filter", "0")
                    )));
                });
                getActions().add(searchButton);
            } else {
                JFXHyperlink mcmodButton = new JFXHyperlink(i18n("mods.mcmod.page"));
                mcmodButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modInfo.getMod()));
                });
                getActions().add(mcmodButton);
            }

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }
    public final class ModInfoListCell extends MDListCell<ModInfoObject> {
        JFXCheckBox checkBox = new JFXCheckBox();
        TwoLineListItem content = new TwoLineListItem();
        JFXButton infoButton = new JFXButton();
        JFXButton revealButton = new JFXButton();
        BooleanProperty booleanProperty;
        ModInfoListCell(JFXListView<ModInfoObject> listView, Holder<Object> lastCell) {
            super(listView, lastCell);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            revealButton.getStyleClass().add("toggle-icon4");
            revealButton.setGraphic(FXUtils.limitingSize(SVG.FOLDER_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(FXUtils.limitingSize(SVG.INFORMATION_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            container.getChildren().setAll(checkBox, content, revealButton, infoButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            if (empty) return;
            content.setTitle(dataItem.getTitle());
            if (dataItem.getMod() != null && I18n.getCurrentLocale().getLocale() == Locale.CHINA) {
                content.getTags().setAll(dataItem.getMod().getDisplayName());
            } else {
                content.getTags().clear();
            }
            content.setSubtitle(dataItem.getSubtitle());
            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.selected);

            if (dataItem.getModInfo() == null && dataItem.canUpdate()) {
                revealButton.setVisible(false);
            } else {
                revealButton.setVisible(true);
                revealButton.setOnMouseClicked(e -> {
                    if (dataItem.getModInfo() != null) {
                        FXUtils.showFileInExplorer(dataItem.getModInfo().getFile());
                    }
                });
            }
            infoButton.setOnMouseClicked(e -> {
                Controllers.dialog(new ModInfoDialog(dataItem));
            });
        }
    }
}
