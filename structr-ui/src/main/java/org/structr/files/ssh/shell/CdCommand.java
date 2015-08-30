package org.structr.files.ssh.shell;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.codehaus.plexus.util.StringUtils;
import org.structr.common.AccessMode;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.files.ssh.StructrShellCommand;
import org.structr.web.entity.Folder;
import org.structr.web.entity.User;

/**
 *
 * @author Christian Morgner
 */
public class CdCommand extends NonInteractiveShellCommand {

	private String target = null;

	@Override
	public void execute(final StructrShellCommand parent) throws IOException {

		final App app = StructrApp.getInstance(SecurityContext.getInstance(user, AccessMode.Backend));
		final Folder currentFolder = parent.getCurrentFolder();

		try (final Tx tx = app.tx()) {

			if (target != null) {

				switch (target) {

					case "..":
						if (currentFolder != null) {
							parent.setCurrentFolder(currentFolder.getProperty(Folder.parent));
						}
						break;

					case ".":
						break;

					case "/":
						parent.setCurrentFolder(null);
						break;

					case "~":
						parent.setCurrentFolder(user.getProperty(User.homeDirectory));
						break;

					default:
						setFolder(parent, currentFolder, target);
						break;
				}

			} else {

				parent.setCurrentFolder(user.getProperty(User.homeDirectory));
			}

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
		}
	}

	@Override
	public void setCommand(final String command) throws IOException {

		if (command.contains(" ") && command.length() > 3) {

			target = command.substring(command.indexOf(" ") + 1);

			if (target.startsWith("\"")) {

				if (target.endsWith("\"")) {

					target = target.substring(1, target.length() - 2);

				} else {

					term.print("Unmatched quotes");
				}
			}

			// remove trailing slash
			if (target != null && target.endsWith("/") && target.length() > 1) {
				target = target.substring(0, target.length() - 1);
			}
		}
	}

	@Override
	public void handleTabCompletion(final StructrShellCommand parent, final String line, final int tabCount) throws IOException {

		if (line.contains(" ") && line.length() > 3) {

			String incompletePath = line.substring(line.indexOf(" ") + 1);
			Folder baseFolder     = null;
			String lastPathPart   = null;

			if (incompletePath.startsWith("\"")) {

				incompletePath = incompletePath.substring(1);
			}

			final App app = StructrApp.getInstance(SecurityContext.getInstance(user, AccessMode.Backend));

			if ("..".equals(incompletePath)) {

				term.handleCharacter('/');
				return;
			}

			if (incompletePath.startsWith("/")) {

				incompletePath = incompletePath.substring(1);

			} else {

				baseFolder = parent.getCurrentFolder();
			}

			// identify full path parts and find folders
			final String[] parts = incompletePath.split("[/]+");
			final int partCount  = parts.length;

			try (final Tx tx = app.tx()) {

				// only a single path part
				if (partCount == 1) {

					lastPathPart = parts[0];

				} else {

					lastPathPart = parts[partCount-1];

					// more than a single path part, find preceding folders
					for (int i=0; i<partCount-1; i++) {

						// skip empty path parts
						if (StringUtils.isNotBlank(parts[i])) {

							baseFolder = app.nodeQuery(Folder.class).and(Folder.parent, baseFolder).and(Folder.name, parts[i]).getFirst();
							if (baseFolder == null) {

								return;
							}
						}
					}
				}

				final List<Folder> allFolders = app.nodeQuery(Folder.class).and(Folder.parent, baseFolder).getAsList();
				final List<Folder> folders    = new LinkedList<>();

				for (final Folder folder : allFolders) {

					if (folder.getName().startsWith(lastPathPart)) {

						folders.add(folder);
					}
				}

				if (folders.size() > 1) {

					// only display autocomplete suggestions after second tab
					if (tabCount > 1) {
						displayAutocompleteSuggestions(parent, folders, line);
					}

				} else {

					final Folder folder = folders.get(0);
					if (lastPathPart.equals(folder.getName())) {

						// only display autocomplete suggestions after second tab
						if (tabCount > 1) {
							displayAutocompleteSuggestions(parent, folder.getProperty(Folder.folders), line);
						}

					} else {

						displayAutocompleteFolder(folder, lastPathPart);
					}
				}

				tx.success();

			} catch (FrameworkException fex) {
				fex.printStackTrace();
			}


		}
	}

	// ----- private methods -----
	private void setFolder(final StructrShellCommand parent, final Folder currentFolder, final String targetFolderName) throws IOException, FrameworkException {

		final App app = StructrApp.getInstance(SecurityContext.getInstance(user, AccessMode.Backend));
		String target = targetFolderName;

		// remove trailing slash
		if (target.endsWith("/")) {
			target = target.substring(0, target.length() - 1);
		}

		if (target.startsWith("/")) {

			final Folder folder = app.nodeQuery(Folder.class).and(Folder.path, target).getFirst();
			if (folder != null) {

				parent.setCurrentFolder(folder);

			} else {

				term.println("Folder " + target + " does not exist");
			}

		} else {

			Folder newFolder = currentFolder;
			boolean found    = false;

			for (final String part : target.split("[/]+")) {

				if (newFolder == null) {

					newFolder = app.nodeQuery(Folder.class).and(Folder.name, part).getFirst();

				} else {

					for (final Folder folder : newFolder.getProperty(Folder.folders)) {

						if (part.equals(folder.getName())) {

							newFolder = folder;
							found     = true;
						}
					}

					if (!found) {

						term.println("Folder " + target + " does not exist");
						return;
					}
				}
			}

			if (newFolder != null) {
				parent.setCurrentFolder(newFolder);
			}
		}
	}

	private void displayAutocompleteFolder(final Folder folder, final String part) throws IOException {

		final String name = folder.getName();
		if (name.startsWith(part)) {

			final String remainder = folder.getName().substring(part.length());
			if (StringUtils.isNotEmpty(remainder)) {

				term.handleString(remainder);
				term.handleCharacter('/');
			}
		}
	}

	private void displayAutocompleteSuggestions(final StructrShellCommand parent, final List<Folder> folders, final String line) throws IOException {

		if (!folders.isEmpty()) {

			term.println();

			for (final Folder folder : folders) {
				term.print(folder.getName() + "/  ");
			}

			term.println();

			parent.displayPrompt();
			term.print(line);
		}
	}
}
