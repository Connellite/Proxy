package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import io.github.connellite.proxy.client.rpc.AdminServiceAsync;
import io.github.connellite.proxy.client.rpc.dto.DirectoryListingDto;
import io.github.connellite.proxy.client.util.Rpc;

/**
 * Modal browser over the server's directories. Starts at {@code initialPath} (or the drive/root
 * list) and hands the chosen absolute path to {@link Callback#onSelected(String)}.
 */
public class DirectoryPickerDialog extends DialogBox {

    public interface Callback {
        void onSelected(String path);
    }

    private final AdminServiceAsync rpc;
    private final Callback callback;
    private final Label currentPath = new Label();
    private final FlowPanel listHost = new FlowPanel();
    private final Button up = new Button("Up");
    private final Button select = new Button("Select this folder");

    private String path;
    private String parent;
    private boolean fallbackTried;

    public DirectoryPickerDialog(AdminServiceAsync rpc, String initialPath, Callback callback) {
        super(false, true);
        this.rpc = rpc;
        this.callback = callback;
        setText("Choose server folder");
        setGlassEnabled(true);
        addStyleName("dir-picker");

        currentPath.setStyleName("dir-picker-path");
        listHost.setStyleName("dir-picker-list");

        up.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                // At a root the parent is null: go back to the list of roots (drives).
                open(parent);
            }
        });
        select.setStyleName("primary");
        select.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (path != null) {
                    hide();
                    DirectoryPickerDialog.this.callback.onSelected(path);
                }
            }
        });
        Button cancel = new Button("Cancel");
        cancel.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                hide();
            }
        });

        FlowPanel toolbar = new FlowPanel();
        toolbar.setStyleName("row-actions");
        toolbar.add(up);
        toolbar.add(currentPath);

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("form-actions");
        actions.add(select);
        actions.add(cancel);

        FlowPanel root = new FlowPanel();
        root.setStyleName("dir-picker-body");
        root.add(toolbar);
        root.add(listHost);
        root.add(actions);
        setWidget(root);

        open(initialPath == null || initialPath.trim().isEmpty() ? null : initialPath.trim());
    }

    private void open(final String target) {
        rpc.listServerDirectories(target, new AsyncCallback<DirectoryListingDto>() {
            @Override
            public void onFailure(Throwable caught) {
                if (target != null && !fallbackTried) {
                    // A typed path may not exist yet: start from the roots instead of an empty dialog.
                    fallbackTried = true;
                    open(null);
                    return;
                }
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(DirectoryListingDto result) {
                fallbackTried = true;
                render(result);
            }
        });
    }

    private void render(DirectoryListingDto listing) {
        path = listing.getPath();
        parent = listing.getParent();
        currentPath.setText(path == null ? "Server drives / roots" : path);
        up.setEnabled(path != null);
        select.setEnabled(path != null);

        listHost.clear();
        if (listing.getDirectories() == null || listing.getDirectories().isEmpty()) {
            Label empty = new Label("No subfolders.");
            empty.setStyleName("hint");
            listHost.add(empty);
        } else {
            for (final String dir : listing.getDirectories()) {
                Anchor link = new Anchor(path == null ? dir : lastSegment(dir), "#");
                link.setTitle(dir);
                link.setStyleName("dir-picker-item");
                link.addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        event.preventDefault();
                        open(dir);
                    }
                });
                listHost.add(link);
            }
        }
        center();
    }

    private static String lastSegment(String dir) {
        int cut = Math.max(dir.lastIndexOf('/'), dir.lastIndexOf('\\'));
        return cut >= 0 && cut < dir.length() - 1 ? dir.substring(cut + 1) : dir;
    }
}
