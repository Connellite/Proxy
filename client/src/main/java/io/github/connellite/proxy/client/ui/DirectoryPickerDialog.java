package io.github.connellite.proxy.client.ui;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.SftpMessages;
import io.github.connellite.proxy.client.rpc.AdminServiceAsync;
import io.github.connellite.proxy.client.rpc.dto.DirectoryListingGWTDTO;
import io.github.connellite.proxy.client.util.Rpc;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal folder tree over the server's file system. Folders load lazily on expand; the dialog opens
 * expanded to {@code initialPath} when it exists. The chosen absolute path goes to
 * {@link Callback#onSelected(String)}.
 */
public class DirectoryPickerDialog extends PopupPanel {

    public interface Callback {
        void onSelected(String path);
    }

    private final AdminServiceAsync rpc;
    private final Callback callback;
    private final Tree tree = new Tree();
    private final TextBox pathBox = new TextBox();
    private final Button select = new Button(AppMessages.COMMON.select());

    public DirectoryPickerDialog(AdminServiceAsync rpc, String initialPath, Callback callback) {
        super(false, true);
        this.rpc = rpc;
        this.callback = callback;
        setStyleName("dir-picker");
        setGlassEnabled(true);
        setGlassStyleName("dir-picker-glass");

        FlowPanel header = new FlowPanel();
        header.setStyleName("dir-picker-header");
        SftpMessages text = AppMessages.SFTP;
        Label title = new Label(text.chooseFolder());
        title.setStyleName("dir-picker-title");
        Button close = new Button("\u00d7");
        close.setStyleName("dir-picker-close");
        close.setTitle(AppMessages.COMMON.close());
        close.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        header.add(title);
        header.add(close);

        // Icons are CSS masks over static/icons/*.svg (see app.css), so their colour stays in CSS.
        Anchor newFolder = new Anchor(text.newFolder(), "#");
        newFolder.setStyleName("dir-picker-new");
        newFolder.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                createFolder();
            }
        });

        tree.addStyleName("dir-tree");
        tree.addSelectionHandler(new SelectionHandler<TreeItem>() {
            @Override
            public void onSelection(SelectionEvent<TreeItem> event) {
                if (event.getSelectedItem() instanceof FolderItem) {
                    pathBox.setText(((FolderItem) event.getSelectedItem()).path);
                    updateSelectState();
                }
            }
        });
        FlowPanel pathRow = new FlowPanel();
        pathRow.setStyleName("dir-picker-path-row");
        InlineLabel pathLabel = new InlineLabel(text.path());
        pathLabel.setStyleName("dir-picker-path-label");
        pathBox.setStyleName("dir-picker-path");
        pathBox.getElement().setAttribute("spellcheck", "false");
        pathBox.addKeyDownHandler(new KeyDownHandler() {
            @Override
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    choose();
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                    hide();
                }
            }
        });
        pathRow.add(pathLabel);
        pathRow.add(pathBox);

        select.setStyleName("primary");
        select.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                choose();
            }
        });
        Button cancel = new Button(AppMessages.COMMON.close());
        cancel.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        FlowPanel footer = new FlowPanel();
        footer.setStyleName("dir-picker-footer");
        footer.add(select);
        footer.add(cancel);

        FlowPanel body = new FlowPanel();
        body.setStyleName("dir-picker-body");
        body.add(newFolder);
        body.add(tree);
        body.add(pathRow);

        FlowPanel root = new FlowPanel();
        root.add(header);
        root.add(body);
        root.add(footer);
        setWidget(root);

        String target = initialPath == null ? "" : initialPath.trim();
        pathBox.setText(target);
        updateSelectState();
        pathBox.addKeyUpHandler(new KeyUpHandler() {
            @Override
            public void onKeyUp(KeyUpEvent event) {
                updateSelectState();
            }
        });
        loadRoots(target);
        center();
    }

    private void loadRoots(final String target) {
        rpc.listServerDirectories(null, new AsyncCallback<DirectoryListingGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(DirectoryListingGWTDTO result) {
                tree.clear();
                List<FolderItem> roots = new ArrayList<FolderItem>();
                for (String root : result.getDirectories()) {
                    FolderItem item = new FolderItem(root, root);
                    roots.add(item);
                    tree.addItem(item);
                }
                FolderItem start = target.isEmpty() ? null : containing(roots, target);
                if (start != null) {
                    revealPath(start, target);
                } else if (roots.size() == 1) {
                    // Unix: a single "/" root, open it like a file manager would.
                    roots.get(0).expand(null);
                    select(roots.get(0));
                }
            }
        });
    }

    /**
     * Expands items from {@code item} down to {@code target}, then selects the deepest match.
     */
    private void revealPath(final FolderItem item, final String target) {
        if (samePath(item.path, target)) {
            select(item);
            scrollIntoView(item);
            return;
        }
        item.expand(new Runnable() {
            @Override
            public void run() {
                FolderItem next = containing(item.children(), target);
                if (next == null) {
                    select(item);
                    scrollIntoView(item);
                } else {
                    revealPath(next, target);
                }
            }
        });
    }

    private void select(FolderItem item) {
        tree.setSelectedItem(item);
    }

    /**
     * Scrolls only the tree box (not the page) so the row sits near the top third.
     */
    private void scrollIntoView(FolderItem item) {
        Element box = tree.getElement();
        int top = item.getAbsoluteTop() - box.getAbsoluteTop() + box.getScrollTop();
        box.setScrollTop(Math.max(0, top - box.getClientHeight() / 3));
    }

    private void updateSelectState() {
        select.setEnabled(!pathBox.getText().trim().isEmpty());
    }

    private void choose() {
        String path = pathBox.getText().trim();
        if (path.isEmpty()) {
            return;
        }
        hide();
        callback.onSelected(path);
    }

    private void createFolder() {
        if (!(tree.getSelectedItem() instanceof FolderItem)) {
            Window.alert(AppMessages.SFTP.selectFolderFirst());
            return;
        }
        final FolderItem parent = (FolderItem) tree.getSelectedItem();
        String name = Window.prompt(AppMessages.SFTP.newFolderPrompt(parent.path), "");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        rpc.createServerDirectory(parent.path, name.trim(), new AsyncCallback<String>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(final String created) {
                parent.reload(new Runnable() {
                    @Override
                    public void run() {
                        for (FolderItem child : parent.children()) {
                            if (samePath(child.path, created)) {
                                select(child);
                                scrollIntoView(child);
                                return;
                            }
                        }
                    }
                });
            }
        });
    }

    private static FolderItem containing(List<FolderItem> items, String target) {
        for (FolderItem item : items) {
            if (isSameOrAncestor(item.path, target)) {
                return item;
            }
        }
        return null;
    }

    private static boolean isSameOrAncestor(String ancestor, String path) {
        if (samePath(ancestor, path)) {
            return true;
        }
        String prefix = ancestor.endsWith("/") || ancestor.endsWith("\\") ? ancestor : ancestor + separator(path);
        return windows(path)
                ? path.toLowerCase().startsWith(prefix.toLowerCase())
                : path.startsWith(prefix);
    }

    private static boolean samePath(String a, String b) {
        String left = trimSeparator(a);
        String right = trimSeparator(b);
        return windows(b) ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private static String trimSeparator(String path) {
        // Keep roots ("/", "C:\") intact.
        if (path.length() > 1 && (path.endsWith("/") || path.endsWith("\\")) && !path.endsWith(":\\")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean windows(String path) {
        return path.indexOf('\\') >= 0 || (path.length() > 1 && path.charAt(1) == ':');
    }

    private static String separator(String path) {
        return windows(path) ? "\\" : "/";
    }

    private static String lastSegment(String dir) {
        String path = trimSeparator(dir);
        int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return cut >= 0 && cut < path.length() - 1 ? path.substring(cut + 1) : path;
    }

    /**
     * One folder: a row (toggle, icon, name) and lazily loaded subfolders.
     */
    private final class FolderItem extends TreeItem {

        private final String path;
        private boolean loaded;
        private boolean loading;

        FolderItem(String path, String label) {
            this.path = path;
            getElement().addClassName("dir-tree-item");
            SafeHtmlBuilder html = new SafeHtmlBuilder();
            html.appendHtmlConstant("<span class=\"dir-tree-icon\"></span><span class=\"dir-tree-name\">");
            html.appendEscaped(label);
            html.appendHtmlConstant("</span>");
            HTML row = new HTML(html.toSafeHtml());
            row.setStyleName("dir-tree-row");
            row.setTitle(path);
            row.addDoubleClickHandler(new DoubleClickHandler() {
                @Override
                public void onDoubleClick(DoubleClickEvent event) {
                    setState(!getState());
                }
            });
            setWidget(row);
            addPlaceholder();
        }

        @Override
        public void setState(boolean open, boolean fireEvents) {
            if (open && !loaded) {
                load(null);
                return;
            }
            super.setState(open, fireEvents);
            updateToggle();
        }

        List<FolderItem> children() {
            List<FolderItem> result = new ArrayList<>();
            for (int i = 0; i < getChildCount(); i++) {
                if (getChild(i) instanceof FolderItem) {
                    result.add((FolderItem) getChild(i));
                }
            }
            return result;
        }

        void expand(final Runnable after) {
            if (loaded) {
                setState(true, false);
                if (after != null) {
                    after.run();
                }
                return;
            }
            load(after);
        }

        void reload(Runnable after) {
            loaded = false;
            load(after);
        }

        private void addPlaceholder() {
            TreeItem placeholder = new TreeItem();
            placeholder.setText("\u2026");
            addItem(placeholder);
        }

        private void updateToggle() {
            Element elem = getElement();
            if (getState() && getChildCount() > 0) {
                elem.addClassName("expanded");
            } else {
                elem.removeClassName("expanded");
            }
            if (loaded && getChildCount() == 0) {
                elem.addClassName("empty");
            } else {
                elem.removeClassName("empty");
            }
        }

        private void load(final Runnable after) {
            if (loading) {
                return;
            }
            loading = true;
            getElement().addClassName("loading");
            rpc.listServerDirectories(path, new AsyncCallback<DirectoryListingGWTDTO>() {
                @Override
                public void onFailure(Throwable caught) {
                    loading = false;
                    getElement().removeClassName("loading");
                    Rpc.showFailure(caught);
                }

                @Override
                public void onSuccess(DirectoryListingGWTDTO result) {
                    loading = false;
                    loaded = true;
                    getElement().removeClassName("loading");
                    removeItems();
                    for (String dir : result.getDirectories()) {
                        addItem(new FolderItem(dir, lastSegment(dir)));
                    }
                    setState(true, false);
                    if (after != null) {
                        after.run();
                    }
                }
            });
        }
    }
}
