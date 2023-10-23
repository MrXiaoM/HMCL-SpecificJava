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

import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.update.ModListUpdatePageSkin.ModInfoObject;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.GithubFileFetch;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ModDownloadUpdatesTask extends Task<List<String>> {

    private final Collection<ModInfoObject> mods;
    private final Collection<Task<String>> dependents;

    public ModDownloadUpdatesTask(String gameVersion, Collection<ModInfoObject> mods) {
        this.mods = mods;
        File root = Profiles.getSelectedProfile().getRepository().getRunDirectory(gameVersion);
        File modsFolder = new File(root, "mods");
        modsFolder.mkdirs();

        dependents = mods.stream()
                .map(mod -> Task.supplyAsync(() -> {
                            try (ByteArrayOutputStream os = GithubFileFetch.fetch(mod.getUpdateInfo().fileName)) {
                                if (os == null) return "!" + mod.getUpdateInfo().modId;
                                LocalModFile modInfo = mod.getModInfo();
                                if (modInfo != null) {
                                    FileUtils.forceDelete(modInfo.getFile().toFile());
                                }
                                FileUtils.writeBytes(new File(modsFolder, mod.getUpdateInfo().fileName), os.toByteArray());
                            }
                            return mod.getUpdateInfo().modId;
                        })
                        .setSignificance(TaskSignificance.MAJOR)
                        .setName(mod.getUpdateInfo().fileName)
                        .withCounter("mods.download"))
                .collect(Collectors.toList());

        setStage("mods.downloads");
        getProperties().put("total", dependents.size());
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() {
        notifyPropertiesChanged();
    }

    @Override
    public Collection<? extends Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        setResult(dependents.stream()
                .filter(task -> task.getResult() != null)
                .map(Task::getResult)
                .collect(Collectors.toList()));
    }
}
