/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2017 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.convert.imports;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import jgnash.engine.EngineFactory;
import jgnash.util.FileUtils;
import jgnash.util.NotNull;
import jgnash.util.OS;

import static jgnash.util.FileUtils.separator;

/**
 * @author Craig Cavanaugh
 */
public class ImportFilter {

    private final static String IMPORT_SCRIPT_DIRECTORY_NAME = "importScripts";

    private static final String JS_REGEX_PATTERN = ".*.js";

    private static String[] KNOWN_SCRIPTS = {"/jgnash/imports/tidy.js"};

    private final ScriptEngine engine;

    private final String script;

    private static final Logger logger = Logger.getLogger(ImportFilter.class.getName());

    ImportFilter(final String script) {
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        this.script = script;
    }

    public static List<ImportFilter> getImportFilters() {
        final List<ImportFilter> importFilterList = new ArrayList<>();

        // known filters first
        for (String knownScript : KNOWN_SCRIPTS) {
            importFilterList.add(new ImportFilter(knownScript));
        }

        for (final Path path : FileUtils.getDirectoryListing(getUserImportScriptDirectory(), JS_REGEX_PATTERN)) {
            importFilterList.add(new ImportFilter(path.toString()));
        }

        final String activeDatabase = EngineFactory.getActiveDatabase();
        if (activeDatabase != null && !activeDatabase.startsWith(EngineFactory.REMOTE_PREFIX)) {
            for (final Path path : FileUtils.getDirectoryListing(getBaseFileImportScriptDirectory(Paths.get(activeDatabase)), JS_REGEX_PATTERN)) {
                importFilterList.add(new ImportFilter(path.toString()));
            }
        }

        return importFilterList;
    }

    public String getScript() {
        return script;
    }

    public String processMemo(final String memo) {

        try (final Reader reader = getReader()) {
            engine.eval(reader);

            final Invocable invocable = (Invocable) engine;

            final Object result = invocable.invokeFunction("processMemo", memo);

            return result.toString();
        } catch (final ScriptException | IOException | NoSuchMethodException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }
        return memo;
    }

    public String processPayee(final String payee) {

        try (final Reader reader = getReader()) {
            engine.eval(reader);

            final Invocable invocable = (Invocable) engine;

            final Object result = invocable.invokeFunction("processPayee", payee);

            return result.toString();
        } catch (final ScriptException | IOException | NoSuchMethodException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }
        return payee;
    }

    public String getDescription() {

        try (final Reader reader = getReader()) {
            engine.eval(reader);

            final Invocable invocable = (Invocable) engine;

            final Object result = invocable.invokeFunction("getDescription", Locale.getDefault());

            return result.toString();
        } catch (final ScriptException | IOException | NoSuchMethodException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }
        return "";
    }

    private Reader getReader() throws IOException {
        if (Files.exists(Paths.get(script))) {
            return Files.newBufferedReader(Paths.get(script));
        }
        return new InputStreamReader(Object.class.getResourceAsStream(script));
    }

    private static Path getUserImportScriptDirectory() {

        String scriptDirectory = System.getProperty("user.home");

        // decode to correctly handle spaces, etc. in the returned path
        try {
            scriptDirectory = URLDecoder.decode(scriptDirectory, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        }

        if (OS.isSystemWindows()) {
            scriptDirectory += separator + "AppData" + separator + "Local" + separator
                    + "jgnash" + separator + IMPORT_SCRIPT_DIRECTORY_NAME;
        } else { // unix, osx
            scriptDirectory += separator + ".jgnash" + separator + IMPORT_SCRIPT_DIRECTORY_NAME;
        }

        logger.log(Level.INFO, "Import Script path: {0}", scriptDirectory);


        return Paths.get(scriptDirectory);
    }

    private static Path getBaseFileImportScriptDirectory(@NotNull final Path baseFile) {
        if (baseFile.getParent() != null) {
           return Paths.get(baseFile.getParent() + separator + IMPORT_SCRIPT_DIRECTORY_NAME);
        }

        return null;
    }
}
