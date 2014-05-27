/*
 * Copyright (c) 2008-2014 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

package com.haulmont.reports.wizard.template.generators;

import com.haulmont.reports.entity.wizard.RegionProperty;
import com.haulmont.reports.entity.wizard.ReportRegion;
import org.apache.poi.ss.util.CellReference;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.OpcPackage;
import org.docx4j.openpackaging.packages.SpreadsheetMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.Parts;
import org.docx4j.openpackaging.parts.SpreadsheetML.Styles;
import org.docx4j.openpackaging.parts.SpreadsheetML.WorksheetPart;
import org.xlsx4j.sml.*;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.util.List;

/**
 * @author fedorchenko
 * @version $Id$
 */
public class XlsxGenerator extends AbstractOfficeGenerator {
    public static final String CELL_MASK = "$%s$%s";
    protected static final String SHEET = "Sheet1"; //PartName can`t contain non-utf symbols cause it used URI encoding and after it looks like %D0%9B%D0%B8%D1%81%D1%821.xml for Лист1.xml

    protected OpcPackage generatePackage() throws Docx4JException, JAXBException {
        SpreadsheetMLPackage pkg = SpreadsheetMLPackage.createPackage();
        //String sheetInternalName = ((Messages) AppBeans.get(Messages.NAME)).getMessage(getClass(), SHEET);
        WorksheetPart sheet = pkg.createWorksheetPart(new PartName("/xl/worksheets/" + SHEET + ".xml"), SHEET, 1);
        SheetData sheetData = sheet.getJaxbElement().getSheetData();
        org.xlsx4j.sml.ObjectFactory factory = org.xlsx4j.jaxb.Context.getsmlObjectFactory();
        CTCalcPr ctCalcPr = factory.createCTCalcPr();
        ctCalcPr.setCalcMode(STCalcMode.AUTO);
        pkg.getWorkbookPart().getJaxbElement().setCalcPr(ctCalcPr);

        Styles styles = new Styles(new PartName("/xl/styles.xml"));
        styles.setJaxbElement(generateStyleSheet(factory));


        DefinedNames definedNames = factory.createDefinedNames();
        long rowNum = 1; //first row of sheet is '1'
        long startedRowForRegion;
        long endedRowForRegion;
        int maxColCount = 0;
        for (ReportRegion reportRegion : reportData.getReportRegions()) {
            if (reportRegion.isTabulatedRegion()) {
                rowNum++;//insert empty row before table
                int colNum = 1;                     //first column of sheet is '1'
                for (RegionProperty regionProperty : reportRegion.getRegionProperties()) {
                    sheetData.getRow().add(createRow(factory, regionProperty.getHierarchicalLocalizedNameExceptRoot(), colNum++, rowNum));
                }
                rowNum++;
                startedRowForRegion = rowNum;
                colNum = 1;
                for (RegionProperty regionProperty : reportRegion.getRegionProperties()) {
                    sheetData.getRow().add(createRow(factory, reportTemplatePlaceholder.getPlaceholderValue(regionProperty.getHierarchicalNameExceptRoot(), reportRegion), colNum++, rowNum));
                }
                endedRowForRegion = rowNum;
                maxColCount = maxColCount > colNum ? maxColCount : colNum;
                rowNum++;
                rowNum++;//insert empty row after table
            } else {
                startedRowForRegion = rowNum;
                for (RegionProperty regionProperty : reportRegion.getRegionProperties()) {
                    Row row = factory.createRow();
                    row.setR(rowNum);
                    row.getC().add(createCell(factory, regionProperty.getHierarchicalLocalizedNameExceptRoot() + ":", 1, rowNum));
                    row.getC().add(createCell(factory, reportTemplatePlaceholder.getPlaceholderValue(regionProperty.getHierarchicalNameExceptRoot(), reportRegion), 2, rowNum));
                    sheetData.getRow().add(row);
                    rowNum++;
                }
                endedRowForRegion = rowNum - 1;
                maxColCount = maxColCount > 2 ? maxColCount : 2;
            }

            addDefinedNames(SHEET, factory, definedNames, startedRowForRegion, endedRowForRegion, reportRegion);
        }
        List<Cols> lstCols = sheet.getJaxbElement().getCols();
        Cols cols = factory.createCols();
        for (int i = 0; i < maxColCount; i++) {
            Col col = factory.createCol();
            col.setMin(i + 1);
            col.setMax(i + 1);
            col.setBestFit(Boolean.TRUE);
            col.setCustomWidth(true);
            col.setWidth(30.);
            cols.getCol().add(col);
        }
        lstCols.add(cols);

        pkg.getWorkbookPart().getJaxbElement().setDefinedNames(definedNames);
        sheet.addTargetPart(styles);
        Parts parts = pkg.getParts();
        Part workBook = parts.get(new PartName("/xl/workbook.xml"));
        workBook.addTargetPart(styles);
        return pkg;
    }

    private void addDefinedNames(String sheetInternalName, ObjectFactory factory, DefinedNames definedNames, long startedRowForRegion, long endedRowForRegion, ReportRegion reportRegion) {
        String regionCellFrom = String.format(CELL_MASK, "A", String.valueOf(startedRowForRegion));
        String regionCellTo = String.format(CELL_MASK,
                (reportRegion.isTabulatedRegion() ? CellReference.convertNumToColString(reportRegion.getRegionProperties().size() - 1) : "B"),
                String.valueOf(endedRowForRegion));
        if (reportRegion.isTabulatedRegion()) {
            //create defined name for a header of table
            CTDefinedName ctDefinedName = factory.createCTDefinedName();
            ctDefinedName.setName(reportRegion.getNameForHeaderBand());
            String regionHeaderCellFrom = String.format(CELL_MASK,
                    "A",
                    String.valueOf(startedRowForRegion - 1));
            String regionHeaderCellTo = String.format(CELL_MASK,
                    CellReference.convertNumToColString(reportRegion.getRegionProperties().size() - 1),
                    String.valueOf(endedRowForRegion - 1));
            ctDefinedName.setValue(sheetInternalName + "!" + regionHeaderCellFrom + ":" + regionHeaderCellTo);
            definedNames.getDefinedName().add(ctDefinedName);
        }
        CTDefinedName ctDefinedName = factory.createCTDefinedName();
        ctDefinedName.setName(reportRegion.getNameForBand());
        ctDefinedName.setValue(sheetInternalName + "!" + regionCellFrom + ":" + regionCellTo);
        definedNames.getDefinedName().add(ctDefinedName);
    }

    protected CTBorder generateBorder(org.xlsx4j.sml.ObjectFactory factory, CTBorderPr borderPr) {
        CTBorder border = factory.createCTBorder();
        border.setBottom(borderPr);
        border.setTop(borderPr);
        border.setLeft(borderPr);
        border.setRight(borderPr);
        return border;
    }

    protected CTXf generateCTXf(Long borderId, Long XfId, Long numFmtId, Long fontId, Long fillId, CTCellAlignment alignment, Boolean applyBorder) {
        CTXf xf = new CTXf();
        if (applyBorder != null) xf.setApplyBorder(applyBorder);
        if (borderId != null) xf.setBorderId(borderId);
        if (XfId != null) xf.setXfId(XfId);
        if (numFmtId != null) xf.setNumFmtId(numFmtId);
        if (fontId != null) xf.setFontId(fontId);
        if (fillId != null) xf.setFillId(fillId);
        if (alignment != null) xf.setAlignment(alignment);
        return xf;
    }

    protected CTStylesheet generateStyleSheet(ObjectFactory factory) {
        CTStylesheet stylesheet = factory.createCTStylesheet();
        CTBorders borders = factory.createCTBorders();
        CTBorderPr borderPr = factory.createCTBorderPr();
        borderPr.setStyle(STBorderStyle.THIN);
        CTColor borderColor = new CTColor();
        borderColor.setIndexed(64l);
        borderPr.setColor(borderColor);
        borders.getBorder().add(generateBorder(factory, factory.createCTBorderPr()));
        borders.getBorder().add(generateBorder(factory, borderPr));
        borders.setCount(2L);
        stylesheet.setBorders(borders);

        stylesheet.setCellXfs(new CTCellXfs());
        stylesheet.getCellXfs().setCount(2L);
        CTCellAlignment cellAlignment = new CTCellAlignment();
        cellAlignment.setWrapText(true);

        stylesheet.getCellXfs().getXf().add(generateCTXf(0l, 0l, null, 0l, 0l, cellAlignment, null));
        stylesheet.getCellXfs().getXf().add(generateCTXf(1l, 0l, null, 0l, 0l, cellAlignment, true));
        stylesheet.setCellStyles(new CTCellStyles());
        stylesheet.getCellStyles().setCount(1L);
        CTCellStyle cellStyle = new CTCellStyle();
        cellStyle.setName("myStyle");
        cellStyle.setBuiltinId(0L);
        cellStyle.setXfId(0L);
        cellStyle.setCustomBuiltin(true);
        stylesheet.getCellStyles().getCellStyle().add(cellStyle);
        stylesheet.setCellStyleXfs(new CTCellStyleXfs());
        stylesheet.getCellStyleXfs().setCount(1L);

        stylesheet.getCellStyleXfs().getXf().add(generateCTXf(0l, null, null, 0l, 0l, null, null));

        stylesheet.setFills(factory.createCTFills());
        CTFill fill = factory.createCTFill();
        CTPatternFill ctPatternFilll = new CTPatternFill();
        ctPatternFilll.setPatternType(STPatternType.NONE);
        fill.setPatternFill(ctPatternFilll);
        stylesheet.getFills().getFill().add(fill);
        stylesheet.getFills().setCount(1L);

        stylesheet.setFonts(factory.createCTFonts());
        CTFont font = new CTFont();
        CTFontSize fdrSize = new CTFontSize();
        fdrSize.setVal(12);
        JAXBElement<CTFontSize> element19 = factory.createCTFontSz(fdrSize);
        font.getNameOrCharsetOrFamily().add(element19);

        CTColor fdrColor = new CTColor();
        fdrColor.setTheme(1L);
        JAXBElement<CTColor> element20 = factory.createCTFontColor(fdrColor);
        font.getNameOrCharsetOrFamily().add(element20);

        CTFontName fdrFontName = new CTFontName();
        fdrFontName.setVal("Times New Roman");
        JAXBElement<CTFontName> element21 = factory.createCTFontName(fdrFontName);
        font.getNameOrCharsetOrFamily().add(element21);
        stylesheet.getFonts().getFont().add(font);
        stylesheet.getFonts().setCount(1L);
        return stylesheet;
    }

}
