package server;

import common.MyMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AuthHandler extends ChannelInboundHandlerAdapter {
    Statement statement;


    //конструктор нужен, чтобы пробросить в Хендлер информацию стейтменте
    public AuthHandler(Statement statement){
        this.statement=statement;
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //читаем канал, видим, что пришел запрос на авторизацию, пришли логин и пароль, отправляем в метод авторизации
        MyMessage in=(MyMessage) msg;
        String command= in.getCommand();
        String[] attachmentsString=in.getAttachmentsStr();
        if (command.equals("/auth")){
            authUser(ctx, attachmentsString);
        }
    }
    private void authUser(ChannelHandlerContext ctx, String[] attachmentsString) {
        try {
            // просим у SQL найти в базу users юзера с таким логином и проверяем, подходит ли пароль
            ResultSet resultSet=statement.executeQuery("SELECT login, password FROM users WHERE login='"+attachmentsString[0]+"'");
            if (resultSet.getString("password").equals(attachmentsString[1])){
                //если пароль подошел, отправляем клиенту ОК, и смело добавляем хендлер-обработчик, а хендлер авторизации удаляется из канала
                ctx.write(new MyMessage("authorization OK"));
                ctx.flush();
                ctx.pipeline().addLast(new DivideHandler(attachmentsString[0]));
                ctx.pipeline().remove(this);
            } else {
                //если пароль не подошёл, уведомляем клиента
                ctx.write(new MyMessage("authorization failed!"));
                ctx.flush();
            }
        } catch (SQLException e) {
            //если такой логин не найден, уведомляем клиента, заодно перехватив исключение от БД
            ctx.write(new MyMessage("authorization failed!"));
            ctx.flush();
        }
    }
}
