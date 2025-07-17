using NTXNFCService.Models;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using uFR;

namespace NFCReaderService.Models;

public static class MRTDDataGroupReader
{
    // MRTD Data Group file identifiers
    private static readonly Dictionary<int, byte[]> DataGroupFiles = new Dictionary<int, byte[]>
{
    { 1, new byte[] { 0x01, 0x01 } },   // DG1 - MRZ
    { 2, new byte[] { 0x01, 0x02 } },   // DG2 - Face
    { 3, new byte[] { 0x01, 0x03 } },   // DG3 - Fingerprints
    { 4, new byte[] { 0x01, 0x04 } },   // DG4 - Iris
    { 5, new byte[] { 0x01, 0x05 } },   // DG5 - Portrait
    { 6, new byte[] { 0x01, 0x06 } },   // DG6 - Reserved
    { 7, new byte[] { 0x01, 0x07 } },   // DG7 - Signature/Mark
    { 8, new byte[] { 0x01, 0x08 } },   // DG8 - Data Features
    { 9, new byte[] { 0x01, 0x09 } },   // DG9 - Structure Features
    { 10, new byte[] { 0x01, 0x0A } },  // DG10 - Substance Features
    { 11, new byte[] { 0x01, 0x0B } },  // DG11 - Additional Personal Details
    { 12, new byte[] { 0x01, 0x0C } },  // DG12 - Additional Document Details
    { 13, new byte[] { 0x01, 0x0D } },  // DG13 - Optional Details
    { 14, new byte[] { 0x01, 0x0E } },  // DG14 - Security Options
    { 15, new byte[] { 0x01, 0x0F } },  // DG15 - Active Authentication Public Key
    { 16, new byte[] { 0x01, 0x10 } }   // DG16 - Persons to Notify
};

    // Special files
    private static readonly byte[] EF_COM = new byte[] { 0x01, 0x1E };    // Common file
    private static readonly byte[] EF_SOD = new byte[] { 0x01, 0x1D };    // Security Object

    /// <summary>
    /// Reads any Data Group using the BAC authenticated session
    /// </summary>
    /// <param name="dgNumber">Data Group number (1-16)</param>
    /// <param name="ksenc">Encryption key from BAC authentication</param>
    /// <param name="ksmac">MAC key from BAC authentication</param>
    /// <param name="sendSequenceCount">Sequence counter (by reference)</param>
    /// <param name="dgData">Output: Raw Data Group data</param>
    /// <returns>Status code</returns>
    public static DL_STATUS ReadDataGroup(int dgNumber, byte[] ksenc, byte[] ksmac, ref ulong sendSequenceCount, out byte[] dgData)
    {
        dgData = null;

        if (!DataGroupFiles.ContainsKey(dgNumber))
        {
            return DL_STATUS.UFR_PARAMETERS_ERROR;
        }

        byte[] fileIndex = DataGroupFiles[dgNumber];
        IntPtr outputPtr = IntPtr.Zero;
        uint outputLength = 0;

        try
        {
            DL_STATUS status = uFCoder.MRTDFileReadBacToHeap(fileIndex, out outputPtr, out outputLength, ksenc, ksmac, ref sendSequenceCount);

            if (status == DL_STATUS.UFR_OK && outputPtr != IntPtr.Zero && outputLength > 0)
            {
                dgData = new byte[outputLength];
                Marshal.Copy(outputPtr, dgData, 0, (int)outputLength);
                // POTENTIAL ADDITION - but be careful:
                 uFCoder.s_block_deselect(50);
            }

            return status;
        }
        finally
        {
            if (outputPtr != IntPtr.Zero)
            {
                uFCoder.DLFree(outputPtr);
            }
        }
    }



    /// <summary>
    /// Reads EF.COM (Common file) containing Data Group list
    /// </summary>
    public static DL_STATUS ReadEFCOM(byte[] ksenc, byte[] ksmac, ref ulong sendSequenceCount, out byte[] comData)
    {
        comData = null;
        IntPtr outputPtr = IntPtr.Zero;
        uint outputLength = 0;

        try
        {
            DL_STATUS status = uFCoder.MRTDFileReadBacToHeap(EF_COM, out outputPtr, out outputLength, ksenc, ksmac, ref sendSequenceCount);

            if (status == DL_STATUS.UFR_OK && outputPtr != IntPtr.Zero && outputLength > 0)
            {
                comData = new byte[outputLength];
                Marshal.Copy(outputPtr, comData, 0, (int)outputLength);
            }

            return status;
        }
        finally
        {
            if (outputPtr != IntPtr.Zero)
            {
                uFCoder.DLFree(outputPtr);
            }
        }
    }

    /// <summary>
    /// Reads EF.SOD (Security Object) containing hashes and signatures
    /// </summary>
    public static DL_STATUS ReadEFSOD(byte[] ksenc, byte[] ksmac, ref ulong sendSequenceCount, out byte[] sodData)
    {
        sodData = null;
        IntPtr outputPtr = IntPtr.Zero;
        uint outputLength = 0;

        try
        {
            DL_STATUS status = uFCoder.MRTDFileReadBacToHeap(EF_SOD, out outputPtr, out outputLength, ksenc, ksmac, ref sendSequenceCount);

            if (status == DL_STATUS.UFR_OK && outputPtr != IntPtr.Zero && outputLength > 0)
            {
                sodData = new byte[outputLength];
                Marshal.Copy(outputPtr, sodData, 0, (int)outputLength);
            }

            return status;
        }
        finally
        {
            if (outputPtr != IntPtr.Zero)
            {
                uFCoder.DLFree(outputPtr);
            }
        }
    }

    /// <summary>
    /// Gets raw data as hex string
    /// </summary>
    public static string GetRawDataAsHex(byte[] data)
    {
        if (data == null || data.Length == 0)
            return string.Empty;

        return BitConverter.ToString(data).Replace("-", " ");
    }

    /// <summary>
    /// Gets raw data as text (attempts UTF-8, falls back to ASCII)
    /// </summary>
    public static string GetRawDataAsText(byte[] data)
    {
        if (data == null || data.Length == 0)
            return string.Empty;

        try
        {
            string utf8Text = Encoding.UTF8.GetString(data);
           
                return utf8Text;
        }
        catch { }

        try
        {
            return Encoding.ASCII.GetString(data);
        }
        catch
        {
            return GetRawDataAsHex(data);
        }
    }
    /// <summary>
    /// Parses DG11 (Additional Personal Details) TLV structure
    /// </summary>
    /// <summary>
    /// Parses DG11 (Additional Personal Details) TLV structure
    /// </summary>


 public static List<Tlv> ParseTlvs(byte[] data)
{
    var tlvs = new List<Tlv>();
    int offset = 0;
    while (offset < data.Length)
    {
        var tlv = ParseTlv(data, ref offset);

        // Check if tag is constructed (first bit of first byte = 1)
        bool isConstructed = (Convert.ToByte(tlv.Tag.Substring(0, 2), 16) & 0x20) != 0;
        if (isConstructed)
        {
            // Recursively parse inner TLVs
            var innerTlvs = ParseTlvs(tlv.Value);
            tlvs.AddRange(innerTlvs);
        }
        else
        {
            tlvs.Add(tlv);
        }
    }
    return tlvs;
}

    private static Tlv ParseTlv(byte[] data, ref int offset)
    {
        // 1. Read Tag
        List<byte> tagBytes = new();
        tagBytes.Add(data[offset++]);

        if ((tagBytes[0] & 0x1F) == 0x1F)
        {
            // Multi-byte tag
            while ((data[offset] & 0x80) == 0x80)
            {
                tagBytes.Add(data[offset++]);
            }
            tagBytes.Add(data[offset++]);
        }

        // 2. Read Length
        int length = 0;
        byte lenByte = data[offset++];
        if ((lenByte & 0x80) == 0)
        {
            length = lenByte;
        }
        else
        {
            int numBytes = lenByte & 0x7F;
            for (int i = 0; i < numBytes; i++)
            {
                length = (length << 8) + data[offset++];
            }
        }

        // 3. Read Value
        byte[] value = new byte[length];
        Array.Copy(data, offset, value, 0, length);
        offset += length;

        return new Tlv
        {
            Tag = BitConverter.ToString(tagBytes.ToArray()).Replace("-", ""),
            Length = length,
            Value = value
        };
    }
    public class Tlv
    {
        public string Tag { get; set; }
        public int Length { get; set; }
        public byte[] Value { get; set; }
    }

    public static Dg11Data ExtractDg11Data(byte[] dg11Bytes)
    {
        var tlvs = ParseTlvs(dg11Bytes);
        var result = new Dg11Data();

        foreach (var tlv in tlvs)
        {
            string tag = tlv.Tag;
            byte[] value = tlv.Value;
            string restData;
            switch (tag)
            {
                case "5F0E": // Full name
                    result.RawFullName = DecodeUtf8(value);
                    var fullParts = result.RawFullName.Split('<', StringSplitOptions.RemoveEmptyEntries);
                    if (fullParts.Length > 0) result.FirstName = fullParts[0];
                    if (fullParts.Length > 1) result.SecondName = fullParts[1];
                    if (fullParts.Length > 2) result.ThirdName = fullParts[2];
                    if (fullParts.Length > 3) result.Lastname = fullParts[3];
                    break;

                case "5F0F": // Mothers's name
                    result.RawMothersName = DecodeUtf8(value);
                    var motherParts = result.RawMothersName.Split('<', StringSplitOptions.RemoveEmptyEntries);
                    if (motherParts.Length > 0) result.MothersFirstName = motherParts[0];
                    //if (fatherParts.Length > 1) result.MotherSurname = fatherParts[1];
                    break;

                case "5F10":
                    result.PersonalIdNumber = Encoding.ASCII.GetString(value);
                    break;

                case "5F11":
                    result.Address = DecodeUtf8(value);
                    break;

                case "A015":
                    if (value.Length >= 3 && value[0] == 0x02 && value[1] == 0x01)
                    {
                        result.Gender = value[2] switch
                        {
                            0x01 => "Male",
                            0x02 => "Female",
                            _ => "Unknown"
                        };
                    }
                    break;
                default:
                    restData = DecodeUtf8(value);
                    break;
            }
        }

        return result;
    }
    public static string ExtractDg1Data(byte[] dg11Bytes)
    {
        var tlvs = ParseTlvs(dg11Bytes);
        string result;
        foreach (var tlv in tlvs)
        {
            string tag = tlv.Tag;
            byte[] value = tlv.Value;

            switch (tag)
            {
                case "5F0E": // Full name
                    result= DecodeUtf8(value);
                    break;

                default:
                    result= DecodeUtf8(value);
                    break;
            }
        }
        return "";

    }

    public static Dg2Data ExtractDG2Data(byte[] dg11Bytes)
    {
        var tlvs = ParseTlvs(dg11Bytes);
        var result = new Dg2Data();

        foreach (var tlv in tlvs)
        {
            string tag = tlv.Tag;
            byte[] value = tlv.Value;

            switch (tag)
            {
                case "5F2E": // Full name
                    result.image = value;
                 
                    break;
                case "87": // Full name
                    result.FormatOwner = value;
                    break;
                case "88": // FormatType
                    result.FormatType = value;

                    break;
                default:
                    result.rawText += DecodeUtf8(value) ;
                     break;
                        
            }
        }

        return result;
    }
    public static string GetFormatDescription(byte[] formatOwner, byte[] formatType)
    {
        string ownerKey = BitConverter.ToString(formatOwner);
        string typeKey = BitConverter.ToString(formatType);

        // Combine keys for easier lookup
        string fullKey = $"{ownerKey}:{typeKey}";

        // Known mappings based on ICAO/ISO specs
        var knownFormats = new Dictionary<string, string>
        {
            { "01-01:00-01", "Facial Image (ISO/IEC 19794-5:2005)" },
            { "01-01:00-08", "Facial Image (ISO/IEC 19794-5:2011)" },
            { "01-01:02-00", "Fingerprint (ISO/IEC 19794-4)" },
            { "01-01:03-00", "Iris Image (ISO/IEC 19794-6)" },
            { "01-01:04-00", "Signature/Handwriting (ISO/IEC 19794-7)" },
            { "01-01:05-00", "Voice (ISO/IEC 19794-13)" },
            { "01-01:06-00", "DNA (ISO/IEC 19794-14)" },
            { "01-01:0B-00", "Additional Format (e.g. Extended Face or Multimodal)" }
            // Add more as needed
        };

        return knownFormats.TryGetValue(fullKey, out var description)
            ? description
            : $"Unknown Format (Owner: {ownerKey}, Type: {typeKey})";
    }

    public static byte[] ExtractJpegImage(byte[] bdbData)
    {
        // JPEG SOI marker bytes
        byte[] jpegHeader = new byte[] { 0xFF, 0xD8 };

        for (int i = 0; i < bdbData.Length - jpegHeader.Length; i++)
        {
            if (bdbData[i] == jpegHeader[0] && bdbData[i + 1] == jpegHeader[1])
            {
                int jpegStartIndex = i;
                int length = bdbData.Length - jpegStartIndex;
                byte[] jpegData = new byte[length];
                Array.Copy(bdbData, jpegStartIndex, jpegData, 0, length);
                return jpegData;
            }
        }

        throw new Exception("JPEG image start marker not found in BDB data.");
    }

    public static string DecodeAndSaveImage(Dg2Data data, string outputFolder, string fileName = "facial_image.jpg")
    {
        if (data.image == null || data.image.Length == 0)
            throw new ArgumentException("imageBDB data is empty");

        byte[] jpegHeader = new byte[] { 0xFF, 0xD8 };

        int startIndex = -1;
        for (int i = 0; i < data.image.Length - jpegHeader.Length; i++)
        {
            if (data.image[i] == jpegHeader[0] && data.image[i + 1] == jpegHeader[1])
            {
                startIndex = i;
                break;
            }
        }

        if (startIndex == -1)
            throw new Exception("JPEG image start marker not found in imageBDB.");

        // Extract JPEG data from startIndex to end
        int length = data.image.Length - startIndex;
        byte[] jpegData = new byte[length];
        Array.Copy(data.image, startIndex, jpegData, 0, length);

        // Ensure output folder exists
        Directory.CreateDirectory(outputFolder);

        string fullPath = Path.Combine(outputFolder, fileName);
        File.WriteAllBytes(fullPath, jpegData);

        return fullPath;
    }
    public static int FindJpeg2000HeaderIndex(byte[] data)
    {
        byte[] header = new byte[] {
        0x00, 0x00, 0x00, 0x0C,
        0x6A, 0x50, 0x20, 0x20,
        0x0D, 0x0A, 0x87, 0x0A
    };

        for (int i = 0; i <= data.Length - header.Length; i++)
        {
            bool found = true;
            for (int j = 0; j < header.Length; j++)
            {
                if (data[i + j] != header[j])
                {
                    found = false;
                    break;
                }
            }
            if (found)
                return i;
        }

        return -1; // Header not found
    }
    private static string DecodeUtf8(byte[] bytes)
    {
        try
        {
            return Encoding.UTF8.GetString(bytes);
        }
        catch
        {
            return "[Invalid UTF-8]";
        }
    }

    /// <summary>
    /// Enhanced TLV length parsing that handles multi-byte lengths
    /// </summary>
    public static int ParseTLVLength(byte[] data, ref int offset)
    {
        if (offset >= data.Length)
            return 0;

        byte firstByte = data[offset++];

        if ((firstByte & 0x80) == 0)
        {
            // Short form - length is in the first byte
            return firstByte;
        }
        else
        {
            // Long form - first byte indicates how many additional bytes encode the length
            int lengthOfLength = firstByte & 0x7F;

            if (lengthOfLength == 0 || offset + lengthOfLength > data.Length)
                return 0;

            int length = 0;
            for (int i = 0; i < lengthOfLength; i++)
            {
                length = (length << 8) | data[offset++];
            }

            return length;
        }
    }

    /// <summary>
    /// Analyzes TLV structure of MRTD data
    /// </summary>
    public static string AnalyzeTLVStructure(byte[] data)
    {
        if (data == null || data.Length == 0)
            return "No data";

        var result = new StringBuilder();
        int offset = 0;

        try
        {
            while (offset < data.Length)
            {
                if (offset + 1 >= data.Length) break;

                byte tag = data[offset++];
                result.AppendLine($"Tag: 0x{tag:X2}");

                if (offset >= data.Length) break;
                byte lengthByte = data[offset++];
                int length;

                if ((lengthByte & 0x80) == 0)
                {
                    length = lengthByte;
                    result.AppendLine($"Length: {length} (short form)");
                }
                else
                {
                    int lengthOfLength = lengthByte & 0x7F;
                    result.AppendLine($"Length of Length: {lengthOfLength}");

                    if (offset + lengthOfLength > data.Length) break;

                    length = 0;
                    for (int i = 0; i < lengthOfLength; i++)
                    {
                        length = (length << 8) | data[offset++];
                    }
                    result.AppendLine($"Length: {length} (long form)");
                }

                if (offset + length <= data.Length && length > 0)
                {
                    byte[] tagData = new byte[length];
                    Array.Copy(data, offset, tagData, 0, length);

                    result.AppendLine($"Data (first 32 bytes as hex): {GetRawDataAsHex(tagData.Take(Math.Min(32, length)).ToArray())}");
                    string textPreview = GetRawDataAsText(tagData);
                    if (textPreview.Length > 100)
                        textPreview = textPreview.Substring(0, 100) + "...";
                    result.AppendLine($"Data as text: {textPreview}");
                    result.AppendLine();

                    offset += length;
                }
                else
                {
                    result.AppendLine("Invalid length or insufficient data");
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            result.AppendLine($"Error analyzing TLV: {ex.Message}");
        }

        return result.ToString();
    }

    /// <summary>
    /// Extracts data from TLV wrapper (removes tag and length headers)
    /// </summary>
    public static byte[] ExtractDataFromTLV(byte[] tlvData)
    {
        if (tlvData == null || tlvData.Length < 2)
            return tlvData;

        try
        {
            int offset = 0;
            byte tag = tlvData[offset++];

            int length;
            byte lengthByte = tlvData[offset++];

            if ((lengthByte & 0x80) == 0)
            {
                length = lengthByte;
            }
            else
            {
                int lengthOfLength = lengthByte & 0x7F;
                length = 0;
                for (int i = 0; i < lengthOfLength; i++)
                {
                    length = (length << 8) | tlvData[offset++];
                }
            }

            if (offset + length <= tlvData.Length)
            {
                byte[] result = new byte[length];
                Array.Copy(tlvData, offset, result, 0, length);
                return result;
            }

            return tlvData;
        }
        catch
        {
            return tlvData;
        }
    }

  

    /// <summary>
    /// Gets the Data Group name/description
    /// </summary>
    public static string GetDataGroupName(int dgNumber)
    {
        return dgNumber switch
        {
            1 => "DG1 - Machine Readable Zone (MRZ)",
            2 => "DG2 - Face Image",
            3 => "DG3 - Fingerprint(s)",
            4 => "DG4 - Iris Image(s)",
            5 => "DG5 - Portrait Image",
            6 => "DG6 - Reserved for Future Use",
            7 => "DG7 - Signature/Mark Image",
            8 => "DG8 - Data Features",
            9 => "DG9 - Structure Features",
            10 => "DG10 - Substance Features",
            11 => "DG11 - Additional Personal Details",
            12 => "DG12 - Additional Document Details",
            13 => "DG13 - Optional Details",
            14 => "DG14 - Security Options",
            15 => "DG15 - Active Authentication Public Key",
            16 => "DG16 - Persons to Notify",
            _ => $"DG{dgNumber} - Unknown"
        };
    }
}