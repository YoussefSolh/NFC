using NFCReaderService.Constants;
using NFCReaderService.Models;
using NTXNFCService.Models;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using uFR;

namespace NFCReaderService.Services
{
    public interface IMRTDService
    {
        Task<NFCReadResult> ReadIDDataAsync(MrzAuthRequest request);
        Task<byte[]?> ReadDG2ImageAsync(byte[] key);
        NFCReadResult ReadDG1Data(byte[] ksenc, byte[] ksmac, ref ulong sendSequenceCount);
        string? ValidateSOD(byte[] ksenc, byte[] ksmac, ref ulong sendSequenceCount, string cscaPath);
    }

    public class MRTDService : IMRTDService
    {
        private readonly ILogger<MRTDService> _logger;
        private readonly IConfiguration _configuration;

        public MRTDService(ILogger<MRTDService> logger, IConfiguration configuration)
        {
            _logger = logger;
            _configuration = configuration;
        }

        public async Task<NFCReadResult> ReadIDDataAsync(MrzAuthRequest request)
        {
            return await Task.Run(() =>
            {
                try
                {
                    // Generate MRZ key from request data
                    byte[] key = new byte[25];
                    var status = uFCoder.MRTD_MRZDataToMRZProtoKey(
                        request.DocumentNumber!,
                        request.DateOfBirth!,
                        request.DateOfExpiry!,
                        key);

                    if (status != DL_STATUS.UFR_OK)
                    {
                        _logger.LogError("Failed to generate MRZ key: {Status} - Check if document details are correct", GetStatusMessage(status));
                        return CreateErrorResult(NFCConstants.UserFriendlyMessages.CheckDocumentDetails);
                    }

                    // Set ISO14443-4 mode
                    status = uFCoder.SetISO14443_4_Mode();
                    // CHANGED: Call s_block_deselect after setting ISO mode to ensure clean state
                    uFCoder.s_block_deselect(50);

                    if (status != DL_STATUS.UFR_OK)
                    {
                        _logger.LogError("Failed to initialize ISO14443-4 mode: {Status}", GetStatusMessage(status));
                        return CreateErrorResult("Failed to initialize card communication. Please try again.");
                    }

                    // Authenticate with BAC
                    byte[] ksenc = new byte[16];
                    byte[] ksmac = new byte[16];
                    ulong sendSequenceCount = 0;

                    status = uFCoder.MRTDAppSelectAndAuthenticateBac(key, ksenc, ksmac, ref sendSequenceCount);
                    if (status != DL_STATUS.UFR_OK)
                    {
                        // CHANGED: Always deselect on authentication failure
                        uFCoder.s_block_deselect(50);
                        _logger.LogError("BAC Authentication failed: {Status} - Document may not be placed correctly or MRZ data is incorrect", GetStatusMessage(status));
                        return CreateErrorResult(NFCConstants.UserFriendlyMessages.CheckDocumentDetails);
                    }

                    // Read DG1 (personal data)
                    var dg1Result = ReadDG1Data(ksenc, ksmac, ref sendSequenceCount);
                    if (!dg1Result.Success)
                    {
                        // CHANGED: Always deselect on DG1 read failure
                        uFCoder.s_block_deselect(50);
                        return dg1Result;
                    }


                    // Parse id card data from DG1
                    var idCardData = ParseDG1Data(dg1Result.DG1Info!);


                    // READING DG2 - Image extraction (commented for future work)
                    var dg2Data = ReadSingleDataGroup(2, key);

                    // Reading DG11 for additional personal details
                    var dg11Data = ReadSingleDataGroup(11, key);
                    var objDg11Data = MRTDDataGroupReader.ExtractDg11Data(dg11Data);

                    // Update ID card data with DG11 information
                    UpdateIDCardDataFromDG11(idCardData, objDg11Data);

                    var objDg2Data = MRTDDataGroupReader.ExtractDG2Data(dg2Data);
                    if (objDg2Data.image == null)
                    {
                        // CHANGED: Always deselect before returning error
                        uFCoder.s_block_deselect(50);
                        _logger.LogError("Exception during ID Card reading");
                        return CreateErrorResult($"Error While reading Image from NFC Chip");
                    }

                    var IsImgJpg = MRTDDataGroupReader.ExtractJpegImage(objDg2Data.image);
                    //string savedImagePath = MRTDDataGroupReader.DecodeAndSaveImage(objDg2Data, @"C:\PassportImages");

                    //To Check which ISO it is being used.
                    var Description = MRTDDataGroupReader.GetFormatDescription(objDg2Data.FormatOwner, objDg2Data.FormatType);

                    // Perform SOD validation if enabled
                    string? validityInfo = null;

                    // CHANGED: Always deselect at the end of successful operation
                    uFCoder.s_block_deselect(50);

                    return new NFCReadResult
                    {
                        Success = true,
                        DG1Info = dg1Result.DG1Info,
                        IDDocumentData = idCardData,
                        IDImage = IsImgJpg,
                        ValidityInfo = validityInfo,
                        ReadTimestamp = DateTime.UtcNow
                    };
                }
                catch (Exception ex)
                {
                    // CHANGED: Always deselect in catch block
                    uFCoder.s_block_deselect(50);
                    _logger.LogError(ex, "Exception during ID Card reading");
                    return CreateErrorResult($"Reading error: {ex.Message}");
                }
            });
        }

        public NFCReadResult ReadDG1Data(byte[] ksenc, byte[] ksmac, ref ulong sendSequenceCount)
        {
            IntPtr dg1 = IntPtr.Zero;
            uint dg1_len = 0;
            byte[] index = new byte[2] { 0x01, 0x01 };

            try
            {
                var status = uFCoder.MRTDFileReadBacToHeap(index, out dg1, out dg1_len, ksenc, ksmac, ref sendSequenceCount);

                if (status != DL_STATUS.UFR_OK)
                {
                    return CreateErrorResult($"Failed to read DG1: {GetStatusMessage(status)}");
                }

                string dg1_info;
                status = uFCoder.MRTDParseDG1ToHeap(out dg1_info, "\n", dg1, dg1_len);

                if (status != DL_STATUS.UFR_OK)
                {
                    return CreateErrorResult($"Failed to parse DG1: {GetStatusMessage(status)}");
                }

                _logger.LogInformation(NFCConstants.UserFriendlyMessages.DocumentReadSuccess);
                return new NFCReadResult { Success = true, DG1Info = dg1_info };
            }
            finally
            {
                // CHANGED: Always free in finally block
                if (dg1 != IntPtr.Zero)
                    uFCoder.DLFree(dg1);
            }
        }

        public async Task<byte[]?> ReadDG2ImageAsync(byte[] key)
        {
            return await Task.Run(() =>
            {
                try
                {
                    // CHANGED: Add initial deselect to ensure clean state
                    uFCoder.s_block_deselect(50);

                    byte[] ksenc = new byte[16];
                    byte[] ksmac = new byte[16];
                    ulong sendSequenceCount = 0;

                    // Fresh BAC session for this Data Group
                    DL_STATUS authStatus = uFCoder.MRTDAppSelectAndAuthenticateBac(key, ksenc, ksmac, ref sendSequenceCount);

                    if (authStatus != DL_STATUS.UFR_OK)
                    {
                        // CHANGED: Always deselect on authentication failure
                        uFCoder.s_block_deselect(50);
                        _logger.LogWarning("BAC Authentication failed for DG2: {Status}", GetStatusMessage(authStatus));
                        return null;
                    }

                    IntPtr dg2, img;
                    uint dg2_len, img_len, img_type;
                    byte[] index2 = new byte[] { 0x01, 0x02 };

                    var status = uFCoder.MRTDFileReadBacToHeap(index2, out dg2, out dg2_len, ksenc, ksmac, ref sendSequenceCount);

                    if (status != DL_STATUS.UFR_OK)
                    {
                        uFCoder.DLFree(dg2);
                        // CHANGED: Always deselect on read failure
                        uFCoder.s_block_deselect(50);
                        _logger.LogWarning("Could not read DG2 (photo): {Status}", GetStatusMessage(status));
                        return null;
                    }

                    status = uFCoder.MRTDGetImageFromDG2(dg2, dg2_len, out img, out img_len, out img_type);
                    uFCoder.DLFree(dg2);

                    if (status != DL_STATUS.UFR_OK)
                    {
                        // CHANGED: Always deselect on image extraction failure
                        uFCoder.s_block_deselect(50);
                        _logger.LogWarning("Could not extract image from DG2: {Status}", GetStatusMessage(status));
                        return null;
                    }

                    var idCardImage = new byte[img_len];
                    Marshal.Copy(img, idCardImage, 0, (int)img_len);

                    // CHANGED: Always deselect at the end of successful operation
                    uFCoder.s_block_deselect(50);
                    return idCardImage;
                }
                catch (Exception ex)
                {
                    // CHANGED: Always deselect in catch block
                    uFCoder.s_block_deselect(50);
                    _logger.LogWarning(ex, "Error reading id card image");
                    return null;
                }
            });
        }

        public string? ValidateSOD(byte[] ksenc, byte[] ksmac, ref ulong sendSequenceCount, string cscaPath)
        {
            try
            {
                string validityInfo;
                var status = uFCoder.MRTDValidate(cscaPath, out validityInfo, "\n",
                    (uint)NFCReaderService.Constants.E_PRINT_VERBOSE_LEVELS.PRINT_ALL_PLUS_STATUSES, ksenc, ksmac, ref sendSequenceCount);

                return validityInfo;
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error validating SOD");
                return "SOD validation failed: " + ex.Message;
            }
        }

        /// <summary>
        /// Extract face image from DG2 data (for future image processing work)
        /// </summary>
        public static byte[] ExtractFaceImageFromDG2(byte[] dg2Bytes)
        {
            int offset = 0;

            // Check for Tag '75' (constructed TLV tag for DG2)
            if (dg2Bytes[offset] == 0x75)
            {
                offset++; // Skip tag

                // Handle length (short or long form)
                int length = 0;
                if ((dg2Bytes[offset] & 0x80) == 0x80)
                {
                    int lenBytes = dg2Bytes[offset++] & 0x7F;
                    for (int i = 0; i < lenBytes; i++)
                    {
                        length = (length << 8) | dg2Bytes[offset++];
                    }
                }
                else
                {
                    length = dg2Bytes[offset++];
                }

                // Now, dg2Bytes[offset .. offset+length] contains the actual image or face info
                return dg2Bytes.Skip(offset).Take(length).ToArray();
            }

            // If no tag, return the raw input
            return dg2Bytes;
        }

        private static byte[] ReadSingleDataGroup(int dgNumber, byte[] key)
        {
            const int maxRetries = 6;
            const int retryDelayMs = 2000;

            for (int attempt = 1; attempt <= maxRetries; attempt++)
            {
                try
                {
                    // KEEP: Deselect at start of each attempt
                    uFCoder.s_block_deselect(50);

                    byte[] ksenc = new byte[16];
                    byte[] ksmac = new byte[16];
                    ulong sendSequenceCount = 0;

                    DL_STATUS authStatus = uFCoder.MRTDAppSelectAndAuthenticateBac(key, ksenc, ksmac, ref sendSequenceCount);

                    if (authStatus != DL_STATUS.UFR_OK)
                    {
                        // REMOVE: Don't deselect immediately after auth failure
                        // uFCoder.s_block_deselect(50); // REMOVE THIS
                        Console.WriteLine($"[Attempt {attempt}] BAC Authentication failed for DG{dgNumber}: {uFCoder.status2str(authStatus)}");
                    }
                    else
                    {
                        if (MRTDDataGroupReader.ReadDataGroup(dgNumber, ksenc, ksmac, ref sendSequenceCount, out byte[] dgData) == DL_STATUS.UFR_OK)
                        {
                            if (dgData != null && dgData.Length > 0)
                            {
                                // KEEP: Deselect before returning success
                                uFCoder.s_block_deselect(50);
                                return dgData;
                            }
                        }

                        // REMOVE: Don't deselect after failed read - will be deselected at start of next loop
                        // uFCoder.s_block_deselect(50); // REMOVE THIS
                    }

                    if (attempt < maxRetries)
                    {
                        Console.WriteLine($"Retrying in {retryDelayMs / 1000} seconds...");
                        Thread.Sleep(retryDelayMs);
                    }
                }
                catch (Exception ex)
                {
                    // KEEP: Deselect in catch block
                    uFCoder.s_block_deselect(50);
                    Console.WriteLine($"[Attempt {attempt}] Exception reading DG{dgNumber}: {ex.Message}");
                }
            }

            // KEEP: Final deselect before returning empty
            uFCoder.s_block_deselect(50);
            return new byte[0];
        }

        private IDDocumentData ParseDG1Data(string dg1Info)
        {
            var lines = dg1Info.Split('\n', StringSplitOptions.RemoveEmptyEntries);
            var idDocumentData = new IDDocumentData();

            foreach (var line in lines)
            {
                var cleanLine = line.Trim().ToUpper();

                if (cleanLine.Contains("NAME OF HOLDER"))
                    idDocumentData.NameOfHolder = ExtractValue(line);
                else if (cleanLine.Contains("DOCUMENT CODE"))
                    idDocumentData.DocumentCode = ExtractValue(line);
                else if (cleanLine.Contains("ISSUING STATE") || cleanLine.Contains("ISSUING COUNTRY"))
                    idDocumentData.IssuingState = ExtractValue(line);
                else if (cleanLine.Contains("DOCUMENT NUMBER"))
                    idDocumentData.DocumentNumber = ExtractValue(line);
                else if (cleanLine.Contains("OPTIONAL DATA") && string.IsNullOrEmpty(idDocumentData.OptionalData1))
                    idDocumentData.OptionalData1 = ExtractValue(line);
                else if (cleanLine.Contains("OPTIONAL DATA") && !string.IsNullOrEmpty(idDocumentData.OptionalData1))
                    idDocumentData.OptionalData2 = ExtractValue(line);
                else if (cleanLine.Contains("DATE OF BIRTH"))
                    idDocumentData.DateOfBirth = DateTime.ParseExact(ExtractValue(line), "dd.MM.yyyy", null);
                else if (cleanLine.Contains("SEX") || cleanLine.Contains("GENDER"))
                    idDocumentData.Sex = ExtractValue(line)[0];
                else if (cleanLine.Contains("DATE OF EXPIRY"))
                    idDocumentData.DateOfExpiry = DateTime.ParseExact(ExtractValue(line), "dd.MM.yyyy", null);
                else if (cleanLine.Contains("NATIONALITY"))
                    idDocumentData.Nationality = ExtractValue(line);
            }

            return idDocumentData;
        }

        private static void UpdateIDCardDataFromDG11(IDDocumentData idCardData, Dg11Data dg11Data)
        {
            idCardData.FirstName = dg11Data.FirstName;
            idCardData.SecondName = dg11Data.SecondName;
            idCardData.ThirdName = dg11Data.ThirdName;
            idCardData.Lastname = dg11Data.Lastname;
            idCardData.MothersFirstName = dg11Data.MothersFirstName;
        }

        private static string ExtractValue(string line)
        {
            var parts = line.Split(':', 2);
            return parts.Length > 1 ? parts[1].Trim().TrimEnd('.') : "";
        }

        private static NFCReadResult CreateErrorResult(string errorMessage)
        {
            return new NFCReadResult
            {
                Success = false,
                ErrorMessage = errorMessage
            };
        }

        private string GetAuthenticationErrorMessage(DL_STATUS status)
        {
            return status switch
            {
                DL_STATUS.UFR_COMMUNICATION_BREAK => "Communication with ID Card was interrupted. Please ensure the id card remains on the reader.",
                DL_STATUS.UFR_APDU_TRANSCEIVE_ERROR => "Failed to communicate with ID Card. Please check ID Card placement and try again.",
                DL_STATUS.MRTD_MRZ_CHECK_ERROR => "Invalid MRZ data. Please verify the document number, date of birth, and expiry date.",
                _ => $"Authentication failed: {GetStatusMessage(status)}. Please verify your MRZ data and ID Card placement."
            };
        }

        private string GetStatusMessage(DL_STATUS status)
        {
            try
            {
                return uFCoder.status2str(status);
            }
            catch
            {
                return status.ToString();
            }
        }
    }
}