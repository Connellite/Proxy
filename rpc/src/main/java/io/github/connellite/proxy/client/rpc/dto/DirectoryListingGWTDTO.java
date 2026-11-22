package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** One level of the server file system for the folder picker. */
@Getter
@Setter
@NoArgsConstructor
public class DirectoryListingGWTDTO implements IsSerializable {

    /** Directory shown; {@code null} for the list of file system roots (drives). */
    private String path;
    /** Parent directory; {@code null} at a root. */
    private String parent;
    /** Absolute paths of subdirectories (or roots). */
    private List<String> directories = new ArrayList<>();
}
