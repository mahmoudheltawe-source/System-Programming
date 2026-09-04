package bgu.spl.net.srv;


import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
public class ConnectionsImpl<T> implements Connections<T> {
    
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnectionHandler;

    public ConnectionsImpl(){
        activeConnectionHandler = new ConcurrentHashMap<>();
    }

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        activeConnectionHandler.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if(activeConnectionHandler.containsKey(connectionId)){
            activeConnectionHandler.get(connectionId).send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        if(activeConnectionHandler.containsKey(connectionId)){
            try{
                activeConnectionHandler.get(connectionId).close();
            }
            catch(IOException ignore){
                ignore.printStackTrace();
            }
            activeConnectionHandler.remove((Integer)connectionId);
        }
        
    }
    
}