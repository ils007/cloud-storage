package common;

import java.io.Serializable;

public class MyMessage implements Serializable {
    private static final long serialVersionUID = 5193392663743561680L;
//это класс-оболочка для обмена сообщениями между клиентом и сервером. Помимо команды мы можем прикладывать что угодно к этому сообщению
    //обязательно должен быть сериализуемый, чтоб кодировать и раскодировать
    private String command;
    private Object[] attachmentsObj;
    private String[] attachmentsStr;

    public String getCommand() {
        return command;
    }

    public Object[] getAttachmentsObj() {
        return attachmentsObj;
    }

    public String[] getAttachmentsStr() {
        return attachmentsStr;
    }

    public MyMessage(String command, Object... attachments) {
        this.command = command;
        this.attachmentsObj=attachments;
    }

    public MyMessage(String command, String... attachmentsStr) {
        this.command = command;
        this.attachmentsStr = attachmentsStr;
    }

    public MyMessage(String command) {
        this.command = command;
    }
}
