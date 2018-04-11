package moe.yo3explorer.vapor;

import moe.yo3explorer.vapor.model.GameInfo;

import javax.ejb.Singleton;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.inject.Named;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedList;

public class DataFetcher
{
    private final boolean LOG_PLAYBACKS = true;
    private static DataFetcher instance;
    private DataFetcher() { }
    public static DataFetcher getInstance()
    {
        if (instance == null)
        {
            instance = new DataFetcher();
        }
        return instance;
    }

    private Context initContext;
    private Context envContext;
    private DataSource ds;

    void setupContext()
    {
        try {
            if (initContext == null) {
                initContext = new InitialContext();
            }
            if (envContext == null) {
                envContext = (Context) initContext.lookup("java:/comp/env");
            }
            if (ds == null) {
                ds = (DataSource) envContext.lookup("jdbc/vaporDb");
            }
        }
        catch (NamingException ne)
        {
            throw new VaporException(ne,"Could not get the context that is necessary to connect to the database.");
        }
    }

    public byte[] getGameDatafile(int i) {
        setupContext();
        try {
            Connection c = ds.getConnection();

            PreparedStatement ps = c.prepareStatement("SELECT datafile FROM games WHERE id=?");
            ps.setInt(1, i);
            ResultSet resultSet = ps.executeQuery();
            if (!resultSet.next()) {
                throw new VaporException("A game with the id %d was not found.", i);
            }
            Blob blob = resultSet.getBlob(1);
            byte[] result = blob.getBytes(1, (int) blob.length());
            resultSet.close();
            ps.close();
            c.close();
            return result;
        }
        catch (SQLException se)
        {
            throw new VaporException(se,"Database error.");
        }
    }

    public byte[] getResource(String hash) {
        setupContext();
        try
        {
            Connection c = ds.getConnection();
            PreparedStatement ps = c.prepareStatement("SELECT data FROM resources WHERE hashdeep=?");
            ps.setString(1,hash);
            ResultSet resultSet = ps.executeQuery();
            if (!resultSet.next()) return null;
            Blob blob = resultSet.getBlob(1);
            byte[] result = blob.getBytes(1,(int)blob.length());
            resultSet.close();
            ps.close();
            c.close();
            return result;
        } catch (SQLException e) {
            throw new VaporException(e,"Database error.");
        }
    }

    public GameInfo getGameInfo(int i) {
        setupContext();
        try {
            Connection c = ds.getConnection();

            PreparedStatement preparedStatement = c.prepareStatement("SELECT title, resource_dependency, id FROM games WHERE id=?");
            preparedStatement.setInt(1, i);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                throw new VaporException("A game with the id %d was not found.", i);
            }
            String title = resultSet.getString(1);
            int resDep = resultSet.getInt(2);
            int id = resultSet.getInt(3);
            resultSet.close();
            preparedStatement.close();
            c.close();

            GameInfo result = new GameInfo();
            result.setTitle(title);
            result.setId(id);
            if (resDep != 0) result.setResourceDependency(getGameInfo(resDep));
            return result;
        } catch(SQLException se)
        {
            throw new VaporException(se,"Database error");
        }
    }

    public LinkedList<GameInfo> getAllPlayableGames() {
        setupContext();
        try {
            LinkedList<GameInfo> result = new LinkedList<>();

            Connection c = ds.getConnection();
            Statement statement = c.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT id, uploaded, title, author FROM games WHERE hide=0");
            while(resultSet.next())
            {
                GameInfo child = new GameInfo();
                child.setId(resultSet.getInt(1));
                child.setTitle(resultSet.getString(3));
                result.add(child);
            }
            resultSet.close();
            statement.close();
            c.close();
            return result;
        } catch (SQLException e) {
            throw new VaporException("SQL Error",e);
        }
    }

    public String getSetting(String getKey)
    {
        setupContext();
        try {
            Connection connection = ds.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT v FROM vapor.settings WHERE k=?");
            ResultSet resultSet = preparedStatement.executeQuery();
            String result = null;
            if (resultSet.next())
            {
                result = resultSet.getString(1);
            }
            resultSet.close();
            preparedStatement.close();
            connection.close();
            return result;
        } catch (SQLException e) {
            throw new VaporException("SQL error",e);
        }
    }

    public void log(int gameId, HttpServletRequest hsr)
    {
        if (!LOG_PLAYBACKS) return;
        setupContext();
        try {
            Connection connection = ds.getConnection();

            PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO playLog (gameId,username,ip) VALUES (?,?,?)");
            preparedStatement.setInt(1,gameId);
            preparedStatement.setString(2,hsr.getRemoteUser());
            preparedStatement.setNull(3,Types.VARCHAR);
            if (hsr.getRemoteAddr().length() < 16)
            {
                preparedStatement.setString(3,hsr.getRemoteAddr());
            }
            boolean result = preparedStatement.execute();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            throw new VaporException("SQL error",e);
        }
    }
}
