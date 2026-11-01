package com.growthprint.backend.invoice.pdf;

/** A rendered invoice PDF and its download file name. Generated on demand; never stored. */
public record InvoicePdf(String filename, byte[] content) {
}
