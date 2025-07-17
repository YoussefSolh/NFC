using System.Text;

namespace NTXNFCService.Models
{
    /// <summary>
    /// Data structure for DG11 (Additional Personal Details)
    /// </summary>
  
        public class Dg11Data
        {
            // Raw Fields
            public string RawFullName { get; set; }
            public string RawMothersName { get; set; }
            public string FirstName { get; set; }
            public string SecondName { get; set; }
            public string ThirdName { get; set; }
            public string Lastname { get; set; }
            public string MothersFirstName { get; set; }
            public string PersonalIdNumber { get; set; }
            public string Address { get; set; }
            public string Gender { get; set; }
        }

    }
