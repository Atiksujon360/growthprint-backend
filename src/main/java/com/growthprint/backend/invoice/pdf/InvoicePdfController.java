package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.common.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

/**
 * Invoice PDF downloads, generated on demand (never stored). URL-level access is already enforced by
 * SecurityConfig ({@code /api/v1/invoices/my/**} CLIENT, {@code /api/v1/invoices/**} ADMIN); the
 * {@code @PreAuthorize} annotations repeat it at method level.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
@SecurityRequirement(name = "bearerAuth")
public class InvoicePdfController {

    private static final String ATTACHMENT = "attachment";
    private static final String INLINE = "inline";

    private final InvoicePdfService pdfService;

    public InvoicePdfController(InvoicePdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download an invoice as PDF (ADMIN)",
            description = "Generated on demand from the stored invoice, line items, client and VERIFIED payment "
                    + "reports. Seller details and payment instructions come from app.company.* configuration.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The PDF",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Invalid disposition",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invoice not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id,
            @Parameter(description = "attachment (default, download) or inline (browser preview)")
            @RequestParam(defaultValue = ATTACHMENT) String disposition) {
        String type = parseDisposition(disposition);
        return pdfResponse(pdfService.renderForAdmin(id), type);
    }

    @GetMapping("/my/{id}/pdf")
    @Operation(summary = "Download one of my invoices as PDF (CLIENT)",
            description = "Only invoices of the logged-in user's client; any other invoice returns 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The PDF",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Invalid disposition, or user has no client",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invoice not found (or not yours)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<byte[]> downloadMine(
            @PathVariable UUID id,
            @Parameter(description = "attachment (default, download) or inline (browser preview)")
            @RequestParam(defaultValue = ATTACHMENT) String disposition,
            @AuthenticationPrincipal String userId) {
        String type = parseDisposition(disposition);
        return pdfResponse(pdfService.renderForClient(id, userId), type);
    }

    private static String parseDisposition(String disposition) {
        String value = disposition == null ? ATTACHMENT : disposition.trim().toLowerCase(Locale.ROOT);
        if (!ATTACHMENT.equals(value) && !INLINE.equals(value)) {
            throw new IllegalArgumentException("disposition must be 'attachment' or 'inline'");
        }
        return value;
    }

    private static ResponseEntity<byte[]> pdfResponse(InvoicePdf pdf, String type) {
        ContentDisposition contentDisposition = ContentDisposition.builder(type).filename(pdf.filename()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(pdf.content());
    }
}
