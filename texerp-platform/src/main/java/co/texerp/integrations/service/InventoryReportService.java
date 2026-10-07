package co.texerp.integrations.service;

import co.texerp.integrations.domain.InventoryStatus;
import co.texerp.integrations.dto.InventoryDtos.InventoryBalanceResponse;
import co.texerp.integrations.dto.InventoryDtos.InventoryPage;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InventoryReportService {

    private static final String[] HEADERS = {
            "SKU",
            "Código producto",
            "Producto",
            "Código bodega",
            "Bodega",
            "Disponible",
            "Reservado",
            "Mínimo",
            "Estado",
            "Bajo stock",
            "Última actualización"
    };

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final InventoryService inventoryService;

    public InventoryReportService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public byte[] generateExcel(
            String sku,
            String product,
            String warehouse,
            String status,
            boolean lowStock,
            int page,
            int size
    ) {
        InventoryPage inventoryPage = inventoryService.search(
                sku,
                product,
                warehouse,
                status,
                lowStock,
                page,
                size
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Inventario");
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle decimalStyle = createDecimalStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            createHeader(sheet, headerStyle);

            int rowIndex = 1;
            for (InventoryBalanceResponse item : inventoryPage.content()) {
                Row row = sheet.createRow(rowIndex++);

                createTextCell(row, 0, item.sku());
                createTextCell(row, 1, item.productCode());
                createTextCell(row, 2, item.productName());
                createTextCell(row, 3, item.warehouseCode());
                createTextCell(row, 4, item.warehouseName());
                createNumberCell(row, 5, item.available(), decimalStyle);
                createNumberCell(row, 6, item.reserved(), decimalStyle);
                createNumberCell(row, 7, item.minimum(), decimalStyle);
                createTextCell(row, 8, translateStatus(item.status()));
                createTextCell(row, 9, item.lowStock() ? "Sí" : "No");

                Cell updatedAtCell = row.createCell(10);
                if (item.updatedAt() != null) {
                    updatedAtCell.setCellValue(java.util.Date.from(item.updatedAt()));
                    updatedAtCell.setCellStyle(dateStyle);
                }
            }

            for (int column = 0; column < HEADERS.length; column++) {
                sheet.autoSizeColumn(column);
                int currentWidth = sheet.getColumnWidth(column);
                sheet.setColumnWidth(column, Math.min(currentWidth + 1024, 255 * 256));
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar el reporte de inventario en Excel", exception);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(
            String sku,
            String product,
            String warehouse,
            String status,
            boolean lowStock,
            int page,
            int size
    ) {
        InventoryPage inventoryPage = inventoryService.search(
                sku,
                product,
                warehouse,
                status,
                lowStock,
                page,
                size
        );

        List<Map<String, ?>> rows = toJasperRows(inventoryPage.content());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_TITLE", "Reporte de inventario");
        parameters.put("REPORT_FILTERS", buildFilterDescription(sku, product, warehouse, status, lowStock));
        parameters.put("TOTAL_RECORDS", inventoryPage.content().size());

        ClassPathResource resource = new ClassPathResource("reports/inventory-report.jrxml");

        try (InputStream inputStream = resource.getInputStream()) {
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    jasperReport,
                    parameters,
                    new JRMapCollectionDataSource(rows)
            );
            return JasperExportManager.exportReportToPdf(jasperPrint);
        } catch (IOException | JRException exception) {
            throw new IllegalStateException("No fue posible generar el reporte de inventario en PDF", exception);
        }
    }

    private void createHeader(Sheet sheet, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);

        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(HEADERS[column]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDecimalStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.000"));
        return style;
    }

    private CellStyle createDateStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
        return style;
    }

    private void createTextCell(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private void createNumberCell(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        cell.setCellStyle(style);
    }

    private List<Map<String, ?>> toJasperRows(List<InventoryBalanceResponse> inventory) {
        List<Map<String, ?>> rows = new ArrayList<>();

        for (InventoryBalanceResponse item : inventory) {
            Map<String, Object> row = new HashMap<>();
            row.put("sku", safe(item.sku()));
            row.put("productCode", safe(item.productCode()));
            row.put("productName", safe(item.productName()));
            row.put("warehouseCode", safe(item.warehouseCode()));
            row.put("warehouseName", safe(item.warehouseName()));
            row.put("available", item.available() == null ? BigDecimal.ZERO : item.available());
            row.put("reserved", item.reserved() == null ? BigDecimal.ZERO : item.reserved());
            row.put("minimum", item.minimum() == null ? BigDecimal.ZERO : item.minimum());
            row.put("status", translateStatus(item.status()));
            row.put("lowStock", item.lowStock() ? "Sí" : "No");
            row.put("updatedAt", item.updatedAt() == null ? "" : DATE_FORMATTER.format(item.updatedAt()));
            rows.add(row);
        }

        return rows;
    }

    private String translateStatus(InventoryStatus status) {
        if (status == null) {
            return "";
        }

        return switch (status) {
            case AVAILABLE -> "Disponible";
            case LOW_STOCK -> "Bajo stock";
            case OUT_OF_STOCK -> "Sin existencia";
        };
    }

    private String buildFilterDescription(
            String sku,
            String product,
            String warehouse,
            String status,
            boolean lowStock
    ) {
        List<String> filters = new ArrayList<>();
        addFilter(filters, "SKU", sku);
        addFilter(filters, "Producto", product);
        addFilter(filters, "Bodega", warehouse);
        addFilter(filters, "Estado", status);

        if (lowStock) {
            filters.add("Solo bajo stock");
        }

        return filters.isEmpty() ? "Sin filtros" : String.join(" | ", filters);
    }

    private void addFilter(List<String> filters, String name, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(name + ": " + value.trim());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
