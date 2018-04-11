using System;
using System.Collections.Generic;
using System.Data.Common;
using System.IO;
using System.Runtime.InteropServices.ComTypes;
using System.Text;
using Ionic.Zip;
using Ionic.Zlib;
using MySql.Data.MySqlClient;

namespace vaporimport
{
    internal class Program
    {
        public static void Main(string[] args)
        {
            if (args.Length != 6)
            {
                Console.WriteLine("Invalid call- Run me with six arguments!");
                Console.WriteLine("vaporImport 127.0.0.1 port dbName dbUsername dbPassword gamePath");
                Console.WriteLine("");
                Console.WriteLine("vaporImport 127.0.0.1 3306 vapor root \"\" \"C:\\Project\\RM2k\\MyGame\"");
                return;
            }
            
            string dbIp = args[0];
            short dbPort = Convert.ToInt16(args[1]);
            string dbName = args[2];
            string dbUsername = args[3];
            string dbPassword = args[4];
            DirectoryInfo gameRoot = new DirectoryInfo(args[5]);

            if (!gameRoot.Exists)
            {
                Console.WriteLine("Could not find path {0}", gameRoot.FullName);
                return;
            }
            FileInfo rpgRtIni = new FileInfo(Path.Combine(gameRoot.FullName, "RPG_RT.ini"));
            if (!rpgRtIni.Exists)
            {
                Console.WriteLine("Could not find RPG_RT.ini in the specified path.");
                return;
            }
            string gameName = ParseRpgRtIni(rpgRtIni);
            if (gameName == null)
            {
                Console.WriteLine("Could not detect game title from RPG_RT.ini");
                return;
            }

            string connString = String.Format("Server={0};Port={1};Database={2};Uid={3};Pwd={4};", dbIp, dbPort, dbName,
                dbUsername, dbPassword);
            DbConnection conn = new MySqlConnection(connString);
            conn.Open();

            Program p = new Program(conn, gameRoot, gameName);
        }

        private DbConnection connection;
        private DbCommand testForResourceCommand;
        private DbCommand uploadResourceCommand;
        private DbCommand uploadGameCommand;
        private DirectoryInfo gameRoot;
        private GameData gameData;
        private ZipFile outZip;
        private int skippedFiles, uploadedFiles;
        private long totalBytes, uploadedBytes;
        
        public Program(DbConnection connection, DirectoryInfo di,string gamename)
        {
            this.connection = connection;
            this.gameRoot = di;
            this.gameData = new GameData();
            
            testForResourceCommand = connection.CreateCommand();
            testForResourceCommand.CommandText = "SELECT originalFilename FROM resources WHERE hashdeep=@hashdeep";
            testForResourceCommand.Parameters.Add(new MySqlParameter("@hashdeep", MySqlDbType.VarChar));
            testForResourceCommand.Prepare();

            uploadResourceCommand = connection.CreateCommand();
            uploadResourceCommand.CommandText =
                "INSERT INTO resources (hashdeep,originalFilename,data) VALUES (@hashdeep,@originalFilename,@data)";
            uploadResourceCommand.Parameters.Add(new MySqlParameter("@hashdeep", MySqlDbType.VarChar));
            uploadResourceCommand.Parameters.Add(new MySqlParameter("@originalFilename", MySqlDbType.VarChar));
            uploadResourceCommand.Parameters.Add(new MySqlParameter("@data", MySqlDbType.Blob));
            uploadResourceCommand.Prepare();

            uploadGameCommand = connection.CreateCommand();
            uploadGameCommand.CommandText = "INSERT INTO games (title,datafile) VALUES (@title,@datafile)";
            uploadGameCommand.Parameters.Add(new MySqlParameter("@title", MySqlDbType.VarChar));
            uploadGameCommand.Parameters.Add(new MySqlParameter("@datafile", MySqlDbType.MediumBlob));
            uploadGameCommand.Prepare();

            outZip = new ZipFile(Encoding.UTF8);
            bool gotDatabase = false, gotMapTree = false, gotExe = false;

            foreach (FileInfo fi in gameRoot.GetFiles())
            {
                if (fi.Name.ToLower().Equals("rpg_rt.ini")) continue;
                if (fi.Name.ToLower().Equals("rpg_rt.exe"))
                {
                    UploadResource(fi, fi.Name);
                    gotExe = true;
                    continue;
                }
                if (fi.Name.ToLower().Equals("rpg_rt.ldb"))
                {
                    outZip.AddFile(fi.FullName, "");
                    gotDatabase = true;
                    continue;
                }
                if (fi.Name.ToLower().Equals("rpg_rt.lmt"))
                {
                    outZip.AddFile(fi.FullName, "");
                    gotMapTree = true;
                    continue;
                }

                if (fi.Name.ToLower().StartsWith("unins")) 
                    continue;
                
                
                switch (fi.Extension.ToLower())
                {
                    case ".lmu":
                        outZip.AddFile(fi.FullName, "");
                        break;
                    case ".dll":
                        UploadResource(fi, fi.Name);
                        break;
                    case ".ico":
                    case ".lsd":
                    case ".log":
                    case ".txt":
                    case ".exe":
                    case ".ind":
                    case ".ini":
                    case ".rtf":
                        break;
                    default:
                        throw new Exception(String.Format("Don't know what to do with extension: {0}", fi.Extension));
                }
            }

            if (!gotExe)
            {
                throw new Exception("Run-time not found.");
            }
            if (!gotMapTree)
            {
                throw new Exception("Map tree data break");
            }
            if (!gotDatabase)
            {
                throw new Exception("Database not found");
            }

            foreach (DirectoryInfo subdir in gameRoot.GetDirectories())
            {
                ScanResourceDir(subdir);
            }
            
            outZip.AddEntry("resources.xml", gameData.Export());
            MemoryStream tempMemoryStream = new MemoryStream();
            outZip.Save(tempMemoryStream);
            byte[] outGameData = tempMemoryStream.Sanitize();
            uploadGameCommand.Parameters["@title"].Value = gamename;
            uploadGameCommand.Parameters["@datafile"].Value = outGameData;
            int result = uploadGameCommand.ExecuteNonQuery();
            if (result == 0)
            {
                throw new Exception("Game upload failed.");
            }
            Console.WriteLine("Game upload succeeded. ({0} files skipped, {1} files total, {2}% compression ratio)",
                skippedFiles, skippedFiles + uploadedFiles, (uploadedBytes * 100) / totalBytes);
            connection.Close();
        }

        public void ScanResourceDir(DirectoryInfo di)
        {
            foreach (FileInfo fi in di.GetFiles())
            {
                if (fi.Name.ToLower().Equals("thumbs.db"))
                    continue;

                if (string.IsNullOrEmpty(fi.Extension.Trim()))
                    continue;

                string withoutPath = Path.GetFileNameWithoutExtension(fi.FullName);
                
                switch (fi.Extension.ToLower())
                {
                    case ".gif":
                    case ".mxc2":
                    case ".jpg":
                        break;
                    case ".png":
                    case ".avi":
                    case ".mid":
                    case ".wav":
                    case ".xyz":
                    case ".bmp":
                    case ".mp3":
                    case ".wma":
                    case ".mpg":
                        UploadResource(fi, string.Format("{0}\\{1}", di.Name, fi.Name));
                        break;
                    default:
                        string linkPath = Path.Combine(fi.Directory.FullName, withoutPath + ".link.wav");
                        FileInfo linkInfo = new FileInfo(linkPath);
                        if (linkInfo.Exists)
                        {
                            UploadResource(linkInfo, string.Format("{0}\\{1}", linkInfo.Directory.Name, linkInfo.Name));
                            break;
                        }                       
                        throw new Exception(String.Format("Don't know what to do with extension: {0}", fi.Extension));
                }
            }
        }

        public void UploadResource(FileInfo fi,string outname)
        {
            totalBytes += fi.Length;
            byte[] bytes = File.ReadAllBytes(fi.FullName);
            string fingerprint = Hashdeep.GetFingerprint(bytes);
            gameData.Add(new IncludeFile(outname, fingerprint));
            
            testForResourceCommand.Parameters["@hashdeep"].Value = fingerprint;
            DbDataReader dbr = testForResourceCommand.ExecuteReader();
            bool fileAlreadyKnown = dbr.HasRows;
            dbr.Close();
            if (fileAlreadyKnown)
            {
                skippedFiles++;
                Console.WriteLine("{0} is already known.", outname);
                return;
            }

            byte[] compressed = GZipStream.CompressBuffer(bytes);
            double percentage = compressed.Length * 100.0 / bytes.Length;
            bool useCompression = percentage > 90;

            byte[] whatToWrite = useCompression ? bytes : compressed;
            uploadResourceCommand.Parameters["@hashdeep"].Value = fingerprint;
            uploadResourceCommand.Parameters["@originalFilename"].Value = outname;
            uploadResourceCommand.Parameters["@data"].Value = whatToWrite;
            int result = uploadResourceCommand.ExecuteNonQuery();
            if (result == 0)
            {
                throw new Exception(String.Format("Upload of {0} failed.", outname));
            }
            uploadedFiles++;
            uploadedBytes += whatToWrite.Length;
            Console.WriteLine("{0} uploaded sucessfully. {2}", outname, result, useCompression ? "Compressed" : "Uncompressed");
        }

        public static string ParseRpgRtIni(FileInfo inFile)
        {
            string[] lines = File.ReadAllLines(inFile.FullName);
            foreach (string line in lines)
            {
                if (line.StartsWith("GameTitle="))
                {
                    string result = line.Substring(10);
                    return result;
                }
            }
            return null;
        }
    }
}