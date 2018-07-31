package client;


import common.MyMessage;
import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.MapValueFactory;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;


public class Controller implements Initializable {

    @FXML
    TextField FieldLogin;
    @FXML
    TextField FieldPass;
    @FXML
    Button ButtonAuth;
    @FXML
    TableView ServerTable;
    @FXML
            Button ButtonRename;
    @FXML
            Button ButtonDelete;
    @FXML
            Button ButtonSendDownloadFile;
    @FXML
            TableView ClientTable;



    ObjectEncoderOutputStream oeos = null;
    Socket socket=null;
    ObjectDecoderInputStream in=null;
    String currentLocalFolder="C:/Test";
    String selectedItem;


    public void sendOrDownload() throws IOException, ClassNotFoundException {
        //кнопку Вверх мы ни отправить, ни получить не можем
        if (selectedItem.equals("<-вверх"));
        //если сейчас выбран элемент из таблицы клиентской части,
            //то файл бьем на байты и отправляем получившийся массив байт на сервер
        else if (!ClientTable.getSelectionModel().isEmpty()) {
            File fileToSend=new File (currentLocalFolder+"/"+selectedItem);
            FileInputStream fis=new FileInputStream(fileToSend);
            byte[] fileInArray = new byte[(int)fileToSend.length()];
            fis.read(fileInArray);
            fis.close();
            oeos.writeObject(new MyMessage("/file",fileInArray,(Object)selectedItem));
            oeos.flush();
        }
        //если выбран элемент из таблицы серверной части, отправляем на сервер запрос, чтобы он прислал этот файл
        else if (!ServerTable.getSelectionModel().isEmpty()) {
            oeos.writeObject(new MyMessage("/download", selectedItem));
            oeos.flush();
        }
    }


    public void delete() throws IOException {
        //кнопку Вверх всё равно удалить не можем
        if (selectedItem.equals("<-вверх"));
        //если выбран файл из клиентского окна, его удаляем
        else if (!ClientTable.getSelectionModel().isEmpty()) {
            File todelete=new File (currentLocalFolder+"/"+selectedItem);
            todelete.delete();
            loadFolder(currentLocalFolder);
        }
        //если выбран файл из серверного окна, то отправляем команду серверу на удаление
        else if (!ServerTable.getSelectionModel().isEmpty()) {
            oeos.writeObject(new MyMessage("/delete",selectedItem));
            oeos.flush();
        }
    }

    public void ClientAreaSelection() throws Exception{
        //если щелкаем по клиентскому окну, очищаем выделение на серверной части, переназываем кнопку, обновляем SelectedItem
        ServerTable.getSelectionModel().clearSelection();
        ButtonSendDownloadFile.setText("Send");
        if (!ClientTable.getSelectionModel().isEmpty()) selectedItem=((Map)ClientTable.getSelectionModel().getSelectedItem()).get("Name").toString();
    }
    //аналогично и серверным окном
    public void ServerAreaSelection() throws Exception{
        ClientTable.getSelectionModel().clearSelection();
        ButtonSendDownloadFile.setText("Download");
        if (!ServerTable.getSelectionModel().isEmpty()) selectedItem=((Map)ServerTable.getSelectionModel().getSelectedItem()).get("Name").toString();
    }

    public void renameFile() throws IOException {
        //кнопку Вверх не переименовываем
        if (selectedItem.equals("<-вверх"));
        //запускаем диалоговое окно, в которое можем ввести новое название файла и если оно ненулевое, то присваиваем новое имя файлу
        //условие сделано для корректной обработки отмены переименования через кнопку Cancel
        else if (!ClientTable.getSelectionModel().isEmpty()) {
            TextInputDialog tid = new TextInputDialog(selectedItem);
            tid.setHeaderText("Переименование");
            tid.setTitle("Переименование");
            tid.setContentText("Введите новое имя файла");
            tid.showAndWait();
            if(tid.getResult()!=null) {
                File unrenamed = new File(currentLocalFolder + "/" + selectedItem);
                File renamed = new File(currentLocalFolder + "/" + tid.getResult());
                unrenamed.renameTo(renamed);
                loadFolder(currentLocalFolder);
            }
        }
        //тоже через диалоговое окно, но отправляем команду серверу, чтоб он поменял имя
        else if (!ServerTable.getSelectionModel().isEmpty()) {
            TextInputDialog tid = new TextInputDialog(selectedItem);
            tid.setHeaderText("Переименование");
            tid.setTitle("Переименование");
            tid.setContentText("Введите новое имя файла");
            tid.showAndWait();
            if(tid.getResult()!=null){
                oeos.writeObject(new MyMessage("/rename",selectedItem,tid.getResult()));
                oeos.flush();
            }

        }
    }

    public void auth() throws IOException {
        //подключение пробуем открыть, только когда жмем кнопку авторизации. Отправляем введенные логин и пароль.

        socket = new Socket("localhost", 8189);
        in = new ObjectDecoderInputStream(socket.getInputStream());
        oeos = new ObjectEncoderOutputStream(socket.getOutputStream());
        String login = FieldLogin.getText();
        String password = FieldPass.getText();
        oeos.writeObject(new MyMessage("/auth", login, password));
        oeos.flush();
        new Service<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        while(true){
                            // Запускаем отдельный сервис на обработку входящих сообщений от сервера
                            // сначала идет блок авторизации
                            MyMessage message = (MyMessage) in.readObject();
                            if(message.getCommand().contains("authorization OK")){
                                FieldPass.setVisible(false);
                                FieldLogin.setVisible(false);
                                ButtonAuth.setVisible(false);
                                break;
                            } else if (message.getCommand().contains("authorization failed!")) {
                                Platform.runLater(()->{
                                    Alert alert=new Alert(Alert.AlertType.ERROR, "неправильный логин/пароль");
                                    alert.showAndWait();
                                });

                            }
                        }
                        //когда авторизация пройдена, можно принимать от сервера файлы и информацию о хранящихся на нем файлах
                        while (true){
                            MyMessage message = (MyMessage) in.readObject();
                            if (message.getCommand().startsWith("/serverFolderList")) {
                                Platform.runLater(()->loadServerFolder(message.getAttachmentsObj()[0],message.getAttachmentsObj()[1]));
                            }
                            else if (message.getCommand().startsWith("/file")){
                                String name=(String) message.getAttachmentsObj()[1];
                                FileOutputStream fos = new FileOutputStream(currentLocalFolder+"/"+name);
                                fos.write((byte[]) message.getAttachmentsObj()[0]);
                                fos.close();
                                System.out.println("пришел файл "+name);
                                Platform.runLater(()->loadFolder(currentLocalFolder));
                            }
                        }
                    }
                };
            }
        }.start();
    }

    //метод для перехода клиентского окна на каталог выше
    public void upFolder() {
        File dir=new File(currentLocalFolder).getParentFile();
        currentLocalFolder=dir.getAbsolutePath();
        loadFolder(currentLocalFolder);
    }
    //метод для перехода серверного окна на каталог выше. По сути просим сервер прислать новый список файлов из каталога выше
    public void askUpFolder () throws IOException {
        oeos.writeObject(new MyMessage("/upfolder"));
        oeos.flush();
    }

    //метод для перехода сервера в выбранную папку
    public void askDownFolder (String name) throws IOException {
        oeos.writeObject(new MyMessage("/downfolder",(String)name));
        oeos.flush();
    }

    //метод для записи списка файлов и их размеров в TableView
    //к нам от сервера пришли два массива одинакового размера (один с названиями, другой с размерами).
    //их записываем в лист мэпов. Создаем два столбца в таблице, каждому присваиваем что туда должно записываться из мэпа.
    //Столбцы добавляем в таблицу, данные добавляем в таблицу.
    public void loadServerFolder(Object a, Object b){
        String[] tempList=(String[]) a;
        String[] sizesOrDirs=(String[]) b;
        ObservableList<Map> allData = FXCollections.observableArrayList();
        for (int j = 0; j < tempList.length; j++) {
            Map<String, String> dataRow = new HashMap<>();
            dataRow.put("Name", tempList[j]);
            dataRow.put("Size", sizesOrDirs[j]);
            allData.add(dataRow);
        }
        TableColumn<Map, String> firstDataColumn = new TableColumn<>("Name");
        TableColumn<Map, String> secondDataColumn = new TableColumn<>("Size");
        firstDataColumn.setCellValueFactory(new MapValueFactory("Name"));
        firstDataColumn.setMinWidth(130);
        secondDataColumn.setCellValueFactory(new MapValueFactory("Size"));
        secondDataColumn.setMinWidth(130);
        ServerTable.getSelectionModel().setCellSelectionEnabled(false);
        ServerTable.getColumns().setAll(firstDataColumn, secondDataColumn);
        ServerTable.setItems(allData);

    }

    public void loadFolder(String pathname) {
        // определяем объект для каталога
        File dir = new File(pathname);
        //вставляем кнопку "вверх" и все содержимое папки, а также создаем параллельный массив с данными о размере файлов или является ли это папкой
        String[] tempList = new String[dir.list().length + 1];
        tempList[0] = "<-вверх";
        System.arraycopy(dir.list(), 0, tempList, 1, dir.list().length);
        String[] sizesOrDirs = new String[dir.list().length + 1];
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
//тут логика та же что с заполнение таблицы серверных файлов
        ObservableList<Map> allData = FXCollections.observableArrayList();
        for (int j = 0; j < tempList.length; j++) {
            Map<String, String> dataRow = new HashMap<>();
            dataRow.put("Name", tempList[j]);
            dataRow.put("Size", sizesOrDirs[j]);
            allData.add(dataRow);
        }
        TableColumn<Map, String> firstDataColumn = new TableColumn<>("Name");
        TableColumn<Map, String> secondDataColumn = new TableColumn<>("Size");
        firstDataColumn.setCellValueFactory(new MapValueFactory("Name"));
        firstDataColumn.setMinWidth(130);
        secondDataColumn.setCellValueFactory(new MapValueFactory("Size"));
        secondDataColumn.setMinWidth(130);
        ClientTable.getSelectionModel().setCellSelectionEnabled(false);
        ClientTable.getColumns().setAll(firstDataColumn, secondDataColumn);
        ClientTable.setItems(allData);

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            loadFolder(currentLocalFolder); //загружаем папку на компьютере клиента
//следующий код позволяет обрабатывать двойные щелчки по таблицам с данными. По щелчку Вверх переходим в каталог выше, по щелчку на папку, заходим в нее
            ClientTable.setRowFactory(tv -> {
                TableRow<Map> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (! row.isEmpty()) ) {
                        Map rowData = row.getItem();
                        if (rowData.get("Name").equals("<-вверх")) upFolder();
                        else if (rowData.get("Size").equals("folder")) {
                            currentLocalFolder=currentLocalFolder+"/"+rowData.get("Name");
                            loadFolder(currentLocalFolder);
                            System.out.println(currentLocalFolder);
                        }
                    }
                });
                return row ;
            });
            //то же самое с сервером. Либо просим его перейти в каталог выше, либо ниже
            ServerTable.setRowFactory(tv -> {
                TableRow<Map> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (! row.isEmpty()) ) {
                        Map rowData = row.getItem();
                        if (rowData.get("Name").equals("<-вверх")) try {
                            askUpFolder();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        else if (rowData.get("Size").equals("folder")) {
                            try {
                                askDownFolder((String) rowData.get("Name"));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                return row ;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
