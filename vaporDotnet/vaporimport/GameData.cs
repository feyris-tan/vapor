using System;
using System.Collections.Generic;
using System.IO;
using System.Xml.Serialization;

namespace vaporimport
{
    public class GameData : List<IncludeFile>
    {
        private static XmlSerializer _xmlSerializer;

        public byte[] Export()
        {
            if (_xmlSerializer == null) _xmlSerializer = new XmlSerializer(typeof(GameData));
            MemoryStream ms = new MemoryStream();
            _xmlSerializer.Serialize(ms, this);

            return ms.Sanitize();
        }
    }

    public class IncludeFile
    {
        public IncludeFile()
        {
        }

        public IncludeFile(string filename, string hash)
        {
            this.filename = filename;
            this.hash = hash;
        }

        [XmlAttribute] public string filename;
        [XmlAttribute] public string hash;
    }
}