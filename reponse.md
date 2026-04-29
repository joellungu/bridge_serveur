# Re: API Integration Questions - Field Mapping Clarifications

Here are the technical confirmations regarding the mapping of fields from FACTS to the Bridge Middleware.

### 1. Client Object
**Question:** *nif: As mentioned in the document, for client types other than PP, the value is automatically populated by the Middleware from the connected company profile. In this case, when sending the request from FACTS, should this field be passed as NULL?*

**Answer:** **NO.**
There is a distinction between the **Issuer NIF** and the **Client NIF**.
*   **Issuer NIF (`invoice.nif`):** This is indeed automatically populated by the Middleware using the connected company's profile.
*   **Client NIF (`client.nif`):** This is the tax ID of the *customer* you are billing. For any client type other than "PP" (Personne Physique), this field is **mandatory**.
    *   If you send `NULL` for a corporate client (PM), the API will reject the request with a validation error.
    *   Please map the actual Tax ID of the customer from FACTS to this field.

### 2. Item Object

**Question:** *Code: Kindly confirm what value is expected here. Should this be the internal reference used by FACTS to identify the billed item or service?*

**Answer:** **YES.**
This field expects your internal article code or SKU (e.g., "SERV-001", "AIR-TKT-001"). It is used to identify the line item.

**Question:** *name: Please confirm if we can provide the service name here (for example: AIR, HOTEL, VISA, etc.).*

**Answer:** **YES.**
This corresponds to the designation or description of the goods/services. Values like "AIR", "HOTEL", "VISA" are perfectly valid.

**Question:** *type: Should this field always be SER (Service) for our use case?*

**Answer:** **YES.**
The system accepts `BIE` (Goods) or `SER` (Services). For travel and booking activities (Airfare, Hotel booking, Visa fees), `SER` is the correct value.

**Question:** *taxGroup: Please provide the corresponding DGI tax group code that should be mapped here.*

**Answer:**
You must map your internal tax codes to the standard DGI single-character codes:
*   **A**: Exempt (Exonéré)
*   **B**: Taxable (16%)
*   **C**: Export
*   **D**: Other specific regimes (if applicable)

*Please ensure your system maps the applicable tax rate to one of these characters.*

**Question:** *quantity: Since the billed quantity can be a decimal value, could you please confirm which units of measure are supported? In our system, we typically use NOS (Number of Services) as the unit.*

**Answer:**
*   **Decimal Values:** **YES**, decimal values are fully supported (e.g., 1.5 days).
*   **Unit of Measure:** You can calculate the quantity based on your "NOS" unit. While the API accepts the numerical quantity correctly, the specific text label "NOS" is currently stored for internal reference but is not strictly validated by the DGI normalization engine. You should send the numeric value corresponding to the Number of Services.
