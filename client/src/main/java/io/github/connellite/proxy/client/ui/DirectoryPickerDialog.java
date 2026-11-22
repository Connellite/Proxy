package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;
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
    private final FlowPanel tree = new FlowPanel();
    private final TextBox pathBox = new TextBox();
    private final Button select = new Button("Select");
    private Node selected;

    public DirectoryPickerDialog(AdminServiceAsync rpc, String initialPath, Callback callback) {
        super(false, true);
        this.rpc = rpc;
        this.callback = callback;
        setStyleName("dir-picker");
        setGlassEnabled(true);
        setGlassStyleName("dir-picker-glass");

        FlowPanel header = new FlowPanel();
        header.setStyleName("dir-picker-header");
        Label title = new Label("Choose server folder");
        title.setStyleName("dir-picker-title");
        Button close = new Button("\u00d7");
        close.setStyleName("dir-picker-close");
        close.setTitle("Close");
        close.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        header.add(title);
        header.add(close);

        // Icons are CSS masks over static/icons/*.svg (see app.css), so their colour stays in CSS.
        Anchor newFolder = new Anchor("New folder", "#");
        newFolder.setStyleName("dir-picker-new");
        newFolder.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                createFolder();
            }
        });

        tree.setStyleName("dir-tree");

        FlowPanel pathRow = new FlowPanel();
        pathRow.setStyleName("dir-picker-path-row");
        InlineLabel pathLabel = new InlineLabel("Path");
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
        Button cancel = new Button("Close");
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
                List<Node> roots = new ArrayList<Node>();
                for (String root : result.getDirectories()) {
                    Node node = new Node(root, root);
                    roots.add(node);
                    tree.add(node);
                }
                Node start = target.isEmpty() ? null : containing(roots, target);
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

    /** Expands nodes from {@code node} down to {@code target}, then selects the deepest match. */
    private void revealPath(final Node node, final String target) {
        if (samePath(node.path, target)) {
            select(node);
            node.scrollIntoView();
            return;
        }
        node.expand(new Runnable() {
            @Override
            public void run() {
                Node next = containing(node.children(), target);
                if (next == null) {
                    select(node);
                    node.scrollIntoView();
                } else {
                    revealPath(next, target);
                }
            }
        });
    }

    private void select(Node node) {
        if (selected != null) {
            selected.setSelected(false);
        }
        selected = node;
        if (node != null) {
            node.setSelected(true);
            pathBox.setText(node.path);
        }
        updateSelectState();
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
        if (selected == null) {
            Window.alert("Select the folder to create the new folder in.");
            return;
        }
        final Node parent = selected;
        String name = Window.prompt("New folder in " + parent.path, "");
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
                        for (Node child : parent.children()) {
                            if (samePath(child.path, created)) {
                                select(child);
                                child.scrollIntoView();
                                return;
                            }
                        }
                    }
                });
            }
        });
    }

    private static Node containing(List<Node> nodes, String target) {
        for (Node node : nodes) {
            if (isSameOrAncestor(node.path, target)) {
                return node;
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

    /** One folder: a row (toggle, icon, name) and a lazily filled list of subfolders. */
    private final class Node extends Composite {

        private final String path;
        private final FlowPanel row = new FlowPanel();
        private final InlineLabel toggle = new InlineLabel();
        private final FlowPanel childPanel = new FlowPanel();
        private final List<Node> children = new ArrayList<>();
        private boolean loaded;
        private boolean loading;
        private boolean expanded;

        Node(String path, String label) {
            this.path = path;
            row.setStyleName("dir-tree-row");
            row.setTitle(path);
            toggle.setStyleName("dir-tree-toggle");
            InlineLabel icon = new InlineLabel();
            icon.setStyleName("dir-tree-icon");
            InlineLabel name = new InlineLabel(label);
            name.setStyleName("dir-tree-name");
            row.add(toggle);
            row.add(icon);
            row.add(name);
            childPanel.setStyleName("dir-tree-children");
            childPanel.setVisible(false);

            toggle.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    event.stopPropagation();
                    if (expanded) {
                        collapse();
                    } else {
                        expand(null);
                    }
                }
            });
            row.addDomHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    select(Node.this);
                }
            }, ClickEvent.getType());
            row.addDomHandler(new DoubleClickHandler() {
                @Override
                public void onDoubleClick(DoubleClickEvent event) {
                    if (expanded) {
                        collapse();
                    } else {
                        expand(null);
                    }
                }
            }, DoubleClickEvent.getType());

            FlowPanel wrapper = new FlowPanel();
            wrapper.add(row);
            wrapper.add(childPanel);
            initWidget(wrapper);
        }

        List<Node> children() {
            return children;
        }

        void setSelected(boolean value) {
            if (value) {
                row.addStyleName("selected");
            } else {
                row.removeStyleName("selected");
            }
        }

        /** Scrolls only the tree box (not the page) so the row sits near the top third. */
        void scrollIntoView() {
            com.google.gwt.dom.client.Element box = tree.getElement();
            int top = row.getElement().getOffsetTop();
            box.setScrollTop(Math.max(0, top - box.getClientHeight() / 3));
        }

        void expand(final Runnable after) {
            if (loaded) {
                show(true);
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

        private void collapse() {
            show(false);
        }

        private void show(boolean value) {
            expanded = value;
            childPanel.setVisible(value && !children.isEmpty());
            if (value) {
                toggle.addStyleName("expanded");
            } else {
                toggle.removeStyleName("expanded");
            }
        }

        private void load(final Runnable after) {
            if (loading) {
                return;
            }
            loading = true;
            row.addStyleName("loading");
            rpc.listServerDirectories(path, new AsyncCallback<DirectoryListingGWTDTO>() {
                @Override
                public void onFailure(Throwable caught) {
                    loading = false;
                    row.removeStyleName("loading");
                    Rpc.showFailure(caught);
                }

                @Override
                public void onSuccess(DirectoryListingGWTDTO result) {
                    loading = false;
                    loaded = true;
                    row.removeStyleName("loading");
                    childPanel.clear();
                    children.clear();
                    for (String dir : result.getDirectories()) {
                        Node child = new Node(dir, lastSegment(dir));
                        children.add(child);
                        childPanel.add(child);
                    }
                    if (children.isEmpty()) {
                        toggle.addStyleName("empty");
                    } else {
                        toggle.removeStyleName("empty");
                    }
                    show(true);
                    if (after != null) {
                        after.run();
                    }
                }
            });
        }
    }
}
