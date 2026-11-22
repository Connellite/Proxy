package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SftpUserRowGWTDTO implements IsSerializable {

    private String userId;
    /** Enabled, not expired, within quota and has ROLE_USER — i.e. can log in over SSH. */
    private boolean usable;
    /** Absolute server folder exposed as {@code /}; {@code null} when SFTP is off for the user. */
    private String root;
    /** List and download only. */
    private boolean readOnly;
}
