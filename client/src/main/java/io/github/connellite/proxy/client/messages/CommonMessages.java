package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface CommonMessages extends Messages {

    @Key("save")
    String save();

    @Key("cancel")
    String cancel();

    @Key("create")
    String create();

    @Key("delete")
    String delete();

    @Key("edit")
    String edit();

    @Key("add")
    String add();

    @Key("close")
    String close();

    @Key("select")
    String select();

    @Key("enabled")
    String enabled();

    @Key("yes")
    String yes();

    @Key("no")
    String no();

    @Key("running")
    String running();

    @Key("stopped")
    String stopped();

    @Key("up")
    String up();

    @Key("down")
    String down();

    @Key("requestFailed")
    String requestFailed();

    @Key("infinity")
    String infinity();

    @Key("dash")
    String dash();

    @Key("never")
    String never();

    @Key("bindAll")
    String bindAll();

    @Key("bindLocalhost")
    String bindLocalhost();

    @Key("bindHint")
    String bindHint();

    @Key("zeroBytes")
    String zeroBytes();

    @Key("zeroRate")
    String zeroRate();

    @Key("bytesWithUnit")
    String bytesWithUnit(String amount, String unit);

    @Key("rate")
    String rate(String bytes);

    @Key("unitB")
    String unitB();

    @Key("unitKb")
    String unitKb();

    @Key("unitMb")
    String unitMb();

    @Key("unitGb")
    String unitGb();

    @Key("unitTb")
    String unitTb();

    @Key("unitPb")
    String unitPb();

    @Key("ratio")
    String ratio(String left, String right);
}
