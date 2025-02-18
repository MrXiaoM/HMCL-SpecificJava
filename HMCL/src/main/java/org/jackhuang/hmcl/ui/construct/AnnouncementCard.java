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
package org.jackhuang.hmcl.ui.construct;

import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;

public class AnnouncementCard extends VBox {

    public AnnouncementCard(String content) {
        TextFlow tf = FXUtils.segmentToTextFlow(content, Controllers::onHyperlinkAction);

        HBox imageBox = new HBox();
        ImageView image = new ImageView("/assets/img/title.png");
        imageBox.getChildren().add(image);
        imageBox.setAlignment(Pos.CENTER);
        imageBox.getStyleClass().add("title");
        getChildren().setAll(imageBox, tf);
        setSpacing(14);
        getStyleClass().addAll("card", "announcement");
    }
}
