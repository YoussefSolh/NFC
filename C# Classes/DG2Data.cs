using System.Text;

namespace NTXNFCService.Models
{
    /// <summary>
    /// Data structure for DG11 (Additional Personal Details)
    /// </summary>
  
        public class Dg2Data
        {
            // Raw Fields
            public byte[] image { get; set; }
        public byte[] FormatOwner { get; set; }
        public byte[] FormatType { get; set; }
        public string? rawText { get; set; }
        }


    }
