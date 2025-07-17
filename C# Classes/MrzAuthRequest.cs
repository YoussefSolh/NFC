using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization;

namespace NFCReaderService.Models;

/// <summary>
/// Request model for MRZ authentication
/// </summary>
public class MrzAuthRequest
{
    [Required]
    [StringLength(9, MinimumLength = 1)]
    [JsonPropertyName("documentNumber")]
    public string? DocumentNumber { get; set; }

    [Required]
    [StringLength(6, MinimumLength = 6)]
    [RegularExpression(@"^\d{6}$", ErrorMessage = "Date of birth must be 6 digits (YYMMDD)")]
    [JsonPropertyName("dateOfBirth")]
    public string? DateOfBirth { get; set; }

    [Required]
    [StringLength(6, MinimumLength = 6)]
    [RegularExpression(@"^\d{6}$", ErrorMessage = "Date of expiry must be 6 digits (YYMMDD)")]
    [JsonPropertyName("dateOfExpiry")]
    public string? DateOfExpiry { get; set; }

    /// <summary>
    /// Optional: Enable SOD (Security Object Document) verification
    /// </summary>
    [JsonPropertyName("verifySOD")]
    public bool VerifySOD { get; set; } = false;

    /// <summary>
    /// Optional: Timeout in seconds for the read operation
    /// </summary>
    [JsonPropertyName("timeoutSeconds")]
    [Range(10, 60)]
    public int TimeoutSeconds { get; set; } = 30;
}

/// <summary>
/// Response model for NFC read operations
/// </summary>
public class NFCReadResult
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("errorMessage")]
    public string? ErrorMessage { get; set; }

    [JsonPropertyName("IDDocumentData")]
    public IDDocumentData? IDDocumentData { get; set; }

    [JsonPropertyName("dg1Info")]
    public string? DG1Info { get; set; }

    [JsonPropertyName("validityInfo")]
    public string? ValidityInfo { get; set; }

    [JsonPropertyName("IDImage")]
    public byte[]? IDImage { get; set; }

    [JsonPropertyName("readTimestamp")]
    public DateTime ReadTimestamp { get; set; }

    [JsonPropertyName("processingTimeMs")]
    public long ProcessingTimeMs { get; set; }

    [JsonPropertyName("warnings")]
    public List<string>? Warnings { get; set; }
}

/// <summary>
/// Parsed id card data from DG1
/// </summary>
public class IDDocumentData
{
    [JsonPropertyName("firstName")]
    public string FirstName { get; set; }

    [JsonPropertyName("secondName")]
    public string SecondName { get; set; }

    [JsonPropertyName("thirdName")]
    public string ThirdName { get; set; }

    [JsonPropertyName("lastname")]
    public string Lastname { get; set; }

    [JsonPropertyName("mothersFirstName")]
    public string MothersFirstName { get; set; }

    [JsonPropertyName("documentCode")]
    public string DocumentCode { get; set; }

    [JsonPropertyName("issuingState")]
    public string IssuingState { get; set; }

    [JsonPropertyName("documentNumber")]
    public string DocumentNumber { get; set; }

    [JsonPropertyName("optionalData1")]
    public string OptionalData1 { get; set; }

    [JsonPropertyName("dateOfBirth")]
    public DateTime DateOfBirth { get; set; }

    [JsonPropertyName("sex")]
    public char Sex { get; set; }

    [JsonPropertyName("dateOfExpiry")]
    public DateTime DateOfExpiry { get; set; }

    [JsonPropertyName("nationality")]
    public string Nationality { get; set; }

    [JsonPropertyName("optionalData2")]
    public string OptionalData2 { get; set; }

    [JsonPropertyName("nameOfHolder")]
    public string NameOfHolder { get; set; }

    [JsonPropertyName("ArabicFullName")]
    public string ArabicFullName { get; set; }

    /// <summary>
    /// Additional raw fields that couldn't be parsed
    /// </summary>
    [JsonPropertyName("additionalFields")]
    public Dictionary<string, string>? AdditionalFields { get; set; }
}

/// <summary>
/// Response model for reader status
/// </summary>
public class ReaderStatusResponse
{
    [JsonPropertyName("connected")]
    public bool Connected { get; set; }

    [JsonPropertyName("readerType")]
    public string? ReaderType { get; set; }

    [JsonPropertyName("firmwareVersion")]
    public string? FirmwareVersion { get; set; }

    [JsonPropertyName("lastChecked")]
    public DateTime LastChecked { get; set; }

    [JsonPropertyName("serviceVersion")]
    public string? ServiceVersion { get; set; }

    [JsonPropertyName("capabilities")]
    public ReaderCapabilities? Capabilities { get; set; }

    [JsonPropertyName("status")]
    public string Status => Connected ? "Ready" : "Disconnected";
}

/// <summary>
/// Reader capabilities and supported features
/// </summary>
public class ReaderCapabilities
{
    [JsonPropertyName("supportsNFC")]
    public bool SupportsNFC { get; set; } = true;

    [JsonPropertyName("supportsBAC")]
    public bool SupportsBAC { get; set; } = true;

    [JsonPropertyName("supportsPACE")]
    public bool SupportsPACE { get; set; } = false;

    [JsonPropertyName("supportsEAC")]
    public bool SupportsEAC { get; set; } = false;

    [JsonPropertyName("maxCardTypes")]
    public List<string>? SupportedCardTypes { get; set; }
}

/// <summary>
/// Test result model
/// </summary>
public class TestResult
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("timestamp")]
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("details")]
    public Dictionary<string, object>? Details { get; set; }

    [JsonPropertyName("duration")]
    public TimeSpan Duration { get; set; }
}

/// <summary>
/// Health check response model
/// </summary>
public class HealthCheckResponse
{
    [JsonPropertyName("service")]
    public string Service { get; set; } = "NFC Reader Local Service";

    [JsonPropertyName("status")]
    public string Status { get; set; } = "Running";

    [JsonPropertyName("version")]
    public string? Version { get; set; }

    [JsonPropertyName("timestamp")]
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("uptime")]
    public TimeSpan Uptime { get; set; }

    [JsonPropertyName("readerConnected")]
    public bool ReaderConnected { get; set; }

    [JsonPropertyName("endpoints")]
    public List<string>? Endpoints { get; set; }
}

/// <summary>
/// Error response model
/// </summary>
public class ErrorResponse
{
    [JsonPropertyName("error")]
    public string? Error { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("timestamp")]
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("requestId")]
    public string? RequestId { get; set; }

    [JsonPropertyName("details")]
    public Dictionary<string, object>? Details { get; set; }
}

/// <summary>
/// Configuration model for NFC reader settings
/// </summary>
public class NFCReaderConfig
{
    public bool UseAdvanced { get; set; } = false;
    public uint ReaderType { get; set; } = 0;
    public string PortName { get; set; } = "";
    public uint PortInterface { get; set; } = 0;
    public string Arg { get; set; } = "";
    public bool EnableSODVerification { get; set; } = false;
    public string CSCAFolderPath { get; set; } = "./csca";
    public int DefaultTimeoutSeconds { get; set; } = 30;
    public bool EnableLogging { get; set; } = true;
    public string LogLevel { get; set; } = "Information";
}

/// <summary>
/// CORS configuration model
/// </summary>
public class CorsConfig
{
    public List<string> AllowedOrigins { get; set; } = new();
    public List<string> AllowedMethods { get; set; } = new() { "GET", "POST", "OPTIONS" };
    public List<string> AllowedHeaders { get; set; } = new() { "Content-Type", "Authorization" };
    public bool AllowCredentials { get; set; } = true;
}

/// <summary>
/// Service configuration model
/// </summary>
public class ServiceConfig
{
    public NFCReaderConfig NFCReader { get; set; } = new();
    public CorsConfig Cors { get; set; } = new();
    public List<int> HttpsPorts { get; set; } = new() { 7001 };
    public List<int> HttpPorts { get; set; } = new() { 7002 };
    public bool EnableSwagger { get; set; } = true;
    public bool EnableHealthChecks { get; set; } = true;
}