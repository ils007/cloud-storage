package server;

import common.MyMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.*;

public class DivideHandler extends ChannelInboundHandlerAdapter {
    private String serverPath="C:\\Users\\Ils\\Desktop\\TestServer";
    private String currentServerFolder;
    String username;

    public DivideHandler(String username){
        this.username=username;
        serverPath=serverPath+"\\"+username;
        currentServerFolder=serverPath;
    }

    @Override
    //если добавили этот Хендлер, значит авторизация прошла успешно и надо выслать список файлов клиенту
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        getFolder(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//кастим-парсим пришедшее сообщение, каждую команду обрабатываем методом или быстрым ответом
        MyMessage in=(MyMessage) msg;
        String command= in.getCommand();
        Object[] attachments=in.getAttachmentsObj();
        String[] attachmentsString=in.getAttachmentsStr();
        if (command.equals("/file")) {
            recieveFile(ctx, attachments);
        }
        else if (command.equals("/delete")) {
            deleteFile(ctx, attachmentsString[0]);
        }
        else if (command.equals("/download")) {
            downloadFile(ctx, attachmentsString[0]);
        }
        else if (command.equals("/rename")){
            renameFile(ctx, attachmentsString);
        }
        else if (command.equals("/upfolder")) {
            File dir=new File(currentServerFolder).getParentFile();
            currentServerFolder=dir.getAbsolutePath();
            getFolder(ctx);
        }
        else if (command.equals("/downfolder")) {
            currentServerFolder=currentServerFolder+"\\"+attachmentsString[0];
            getFolder(ctx);
        }
        else if (command.equals("/getFolder")) {
            getFolder(ctx);
        }
    }

    //метод обрабатывающий пришедший файл. создаем новый файл, пишем из байтового массива в этот файл
    private void recieveFile(ChannelHandlerContext ctx, Object[] attachments) throws IOException {
        String name=(String) attachments[1];
        FileOutputStream fos = new FileOutputStream(currentServerFolder+"\\"+name);
        fos.write((byte[]) attachments[0]);
        fos.close();
        System.out.println("пришел файл "+name);
        getFolder(ctx);
    }

    //метод скачики файла с сервера. Сервер разбиваем файл на байты и пишет в массив, отправляем клиенту массив и название файла
    private void downloadFile(ChannelHandlerContext ctx, String s) throws IOException {
        File fileToSend=new File (currentServerFolder+"\\"+ s);
        FileInputStream fis=new FileInputStream(fileToSend);
        byte[] fileInArray = new byte[(int)fileToSend.length()];
        System.out.println("отправлен файл "+s+" размером "+fileToSend.length()+" байт");
        fis.read(fileInArray);
        fis.close();
        ctx.write(new MyMessage("/file",fileInArray, s));
        ctx.flush();
    }

    //метод, переименовывающий файл на сервере, если такая команда поступила от клиента
    private void renameFile(ChannelHandlerContext ctx, String[] attachmentsString) {
        File unrenamed=new File (currentServerFolder+"\\"+attachmentsString[0]);
        File renamed= new File (currentServerFolder+"\\"+attachmentsString[1]);
        unrenamed.renameTo(renamed);
        getFolder(ctx);
        System.out.println("changed name from "+attachmentsString[0]+" to " + attachmentsString[1]);
    }

    //метод удаления файла на сервере по команде клиента
    private void deleteFile(ChannelHandlerContext ctx, String s) {
        File todelete=new File (currentServerFolder+"\\"+ s);
        todelete.delete();
        getFolder(ctx);
        System.out.println(s +" was deleted");
    }

    //метод, составляющий два массива Стрингов (первый с названиями файлов и папок, второй с их размерами или указанием папка ли это)
    //отправляем эти массивы клиенту в виде объектов
    //конструкция с условиями проверяет, находимся ли в папке данного юзера и не позволяет ему выйти в папку выше, чтобы он свободно не бродил по всем папкам сервера
    private void getFolder(ChannelHandlerContext ctx) {
        File dir = new File(currentServerFolder);
        System.out.println(currentServerFolder);
        int plusrow=0;
        if (!currentServerFolder.equals(serverPath)) plusrow=1;
        String[] folderArr=new String[dir.list().length+plusrow];
        if (!currentServerFolder.equals(serverPath)) {
            folderArr[0]="<-вверх";
            System.arraycopy(dir.list(),0,folderArr,1,dir.list().length);
        } else folderArr=dir.list();

        String[] sizesOrDirs = new String[dir.list().length + plusrow];
        if (!currentServerFolder.equals(serverPath)) {
            sizesOrDirs[0] = " ";
            int i = 1;
            for (File item : dir.listFiles()) {
                if (item.isDirectory()) {
                    sizesOrDirs[i] = "folder";
                } else {
                    sizesOrDirs[i] = item.length() / 1024 + "kb";
                }
                i++;
            }
        } else {
            int i = 0;
            for (File item : dir.listFiles()) {
                if (item.isDirectory()) {
                    sizesOrDirs[i] = "folder";
                } else {
                    sizesOrDirs[i] = item.length() / 1024 + "kb";
                }
                i++;
            }
        }

        ctx.write(new MyMessage("/serverFolderList",(Object) folderArr,(Object) sizesOrDirs));
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
