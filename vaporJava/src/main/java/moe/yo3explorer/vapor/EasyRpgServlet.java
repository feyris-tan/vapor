package moe.yo3explorer.vapor;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import moe.yo3explorer.vapor.model.GameInfo;
import moe.yo3explorer.vapor.model.xml.ArrayOfIncludeFile;
import moe.yo3explorer.vapor.model.xml.IncludeFile;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.persistence.criteria.CriteriaBuilder;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.crypto.Data;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class EasyRpgServlet extends HttpServlet
{
    private static ClassLoader classLoader;
    private static XStream xStream;
    private static Logger logger;

    static
    {
        classLoader = EasyRpgServlet.class.getClassLoader();

        xStream = new XStream(new DomDriver());
        xStream.alias("ArrayOfIncludeFile", ArrayOfIncludeFile.class);
        xStream.alias("IncludeFile", IncludeFile.class);
        xStream.addImplicitCollection(ArrayOfIncludeFile.class,"IncludeFile",IncludeFile.class);
        xStream.useAttributeFor(IncludeFile.class,"filename");
        xStream.useAttributeFor(IncludeFile.class,"hash");

        logger = Logger.getLogger(EasyRpgServlet.class.getName());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String rawReqUri = req.getRequestURI();
        String context = req.getContextPath();
        rawReqUri = rawReqUri.substring(context.length());
        System.out.println(rawReqUri);
        if (!rawReqUri.startsWith("/play/"))
        {
            throw new ServletException("Servlet called from invalid root.");
        }
        rawReqUri = rawReqUri.substring(6);
        if (rawReqUri.contains(";jsessionid="))
        {
            rawReqUri = rawReqUri.substring(0,rawReqUri.lastIndexOf(";jsessionid="));
        }
        String[] args = rawReqUri.split("/");

        if (args.length == 1)
        {
            resp.sendRedirect(rawReqUri + "/index.html");
            return;
        }
        if (args.length == 2 && args[1].equals("index.html"))
        {
            InputStream is = classLoader.getResourceAsStream("easyrpg-js.html");
            writeOutput(resp, is);
            return;
        }
        if (args.length == 2 && args[1].equals("index.html.mem"))
        {
            resp.addHeader("Content-Encoding","gzip");
            InputStream is = classLoader.getResourceAsStream("index.html.mem.gz");
            writeOutput(resp, is);
            return;
        }
        if (args.length == 2 && args[1].equals("index.js"))
        {
            resp.addHeader("Content-Encoding","gzip");
            InputStream is = classLoader.getResourceAsStream("index.js.gz");
            writeOutput(resp, is);
            return;
        }
        if (rawReqUri.endsWith("/games/default/index.json"))
        {
            int gameId = Integer.parseInt(args[0]);
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            objectBuilder.add("RPG_RT.ini","RPG_RT.ini");
            makeIndexJson(gameId, objectBuilder);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            JsonWriter writer = Json.createWriter(resp.getWriter());
            writer.writeObject(objectBuilder.build());
            resp.getWriter().flush();
            resp.getWriter().close();
            DataFetcher.getInstance().log(gameId,req);
            return;
        }
        if (args.length == 4 && args[3].equals("RPG_RT.ini"))
        {
            int gameId = Integer.parseInt(args[0]);
            GameInfo gi = DataFetcher.getInstance().getGameInfo(gameId);
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            String result = "[RPG_RT]\n" +
                    "GameTitle=%s\n" +
                    "MapEditMode=2\n" +
                    "MapEditZoom=0\n" +
                    "FullPackageFlag=1\n";
            result = String.format(result,gi.getTitle());
            resp.getWriter().write(result);
            resp.getWriter().flush();
            resp.getWriter().close();
            return;
        }
        if (args.length >= 4)
        {
            int fileNameVines = args.length - 3;
            String[] arrCopy = new String[fileNameVines];
            System.arraycopy(args,3,arrCopy,0,fileNameVines);
            String filename = String.join("/",arrCopy);
            if (filename.equals("resources.xml"))
            {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().close();
                return;
            }
            int gameId = Integer.parseInt(args[0]);
            byte[] data = fetchFromGame(gameId,filename);
            if (data == null)
            {
                logger.warning(String.format("Could not load file \"%s\" from game #%d",filename,gameId));
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getOutputStream().flush();
                resp.getOutputStream().close();
                return;
            }
            if (data[0] == 31 && data[1] == -117 && data[2] == 8)
            {
                resp.addHeader("Content-Encoding","gzip");
            }
            if (data != null) {
                writeOutput(resp,new ByteArrayInputStream(data));
                return;
            }
        }
        System.out.println(String.format("%s (args-len: %d)",rawReqUri,args.length));
        super.doGet(req, resp);
    }

    private String decode(String file) throws UnsupportedEncodingException {
        file = URLDecoder.decode(file,"utf-8");
        return file;
    }
    private byte[] fetchFromGame(int gameId, String file) throws IOException {
        file = decode(file);
        file = file.toLowerCase();
        DataFetcher instance = DataFetcher.getInstance();
        GameInfo gi = instance.getGameInfo(gameId);
        byte[] gamedata = instance.getGameDatafile(gameId);
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(gamedata));
        while (true)
        {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) break;
            if (entry.getName().toLowerCase().equals(file))
            {
                byte[] rawFile = extractFromZip(zis,entry);
                return rawFile;
            }
            if (entry.getName().toLowerCase().equals("resources.xml"))
            {
                ArrayOfIncludeFile includeFiles = parseResourceXmlFromZip(zis, entry);
                for (IncludeFile included: includeFiles.IncludeFile)
                {
                    String value = included.filename;
                    value = value.replace('\\','/');
                    value = value.toLowerCase();
                    String key = value;
                    if (key.contains("/"))
                        key = key.substring(0,value.lastIndexOf('.'));
                    if (key.equals(file) || value.equals(file))
                    {
                        return instance.getResource(included.hash);
                    }
                }
                continue;
            }
        }
        zis.close();
        if (gi.getResourceDependency() != null)
        {
            return fetchFromGame(gi.getResourceDependency().getId(),file);
        }
        return null;
    }

    private ArrayOfIncludeFile parseResourceXmlFromZip(ZipInputStream zis, ZipEntry entry) throws IOException {
        byte[] rawXml = extractFromZip(zis, entry);
        ByteArrayInputStream bais = new ByteArrayInputStream(rawXml);
        Object includesBoxed = xStream.fromXML(bais);
        bais.close();
        return (ArrayOfIncludeFile)includesBoxed;
    }

    private void makeIndexJson(int gameId, JsonObjectBuilder objectBuilder) throws IOException {
        DataFetcher instance = DataFetcher.getInstance();
        GameInfo gi = instance.getGameInfo(gameId);
        byte[] gamedata = instance.getGameDatafile(gameId);
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(gamedata));

        while (true)
        {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) break;
            if (entry.getName().toLowerCase().equals("resources.xml"))
            {
                ArrayOfIncludeFile includeFiles = parseResourceXmlFromZip(zis, entry);
                for (IncludeFile file: includeFiles.IncludeFile)
                {
                    String value = file.filename;
                    value = value.replace('\\','/');
                    String key = value;
                    if (key.contains("/"))
                        key = key.substring(0,value.lastIndexOf('.'));
                    objectBuilder.add(key,value);
                }
                continue;
            }
            objectBuilder.add(entry.getName(),entry.getName());
        }

        GameInfo rtp = gi.getResourceDependency();
        if (rtp != null)
        {
            makeIndexJson(rtp.getId(),objectBuilder);
        }
    }

    private byte[] extractFromZip(ZipInputStream zis, ZipEntry entry) throws IOException {
        int rawXmlLen = (int)entry.getSize();
        int copyLen = rawXmlLen;
        byte[] rawXml = new byte[rawXmlLen];
        while (copyLen > 0) {
            copyLen -= zis.read(rawXml,rawXmlLen - copyLen,copyLen);
        }
        return rawXml;
    }

    private void writeOutput(HttpServletResponse resp, InputStream is) throws IOException {
        /*int copySize = 2048;
        byte[] copyBuffer = new byte[copySize];
        while (copySize != 0)
        {
            copySize = is.read(copyBuffer,0,copySize);
            resp.getOutputStream().write(copyBuffer,0,copySize);
        }
        is.close();
        resp.getOutputStream().flush();
        resp.getOutputStream().close();*/
        int copySize = 2048;
        byte[] copyBuffer = new byte[copySize];
        while (is.available() > 0)
        {
            copySize = Math.min(copySize,is.available());
            copySize = is.read(copyBuffer,0,copySize);
            resp.getOutputStream().write(copyBuffer,0,copySize);
        }
        is.close();
        resp.getOutputStream().flush();
        resp.getOutputStream().close();
    }
}
