package org.motechproject.nms.region.utils;

import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.ICsvReader;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class CsvImporter<R extends ICsvReader> implements Closeable {

    private R csvReader;
    private String[] fieldNames;
    private CellProcessor[] processors;

    public void open(Reader reader, Map<String, CellProcessor> processorMapping) throws IOException {
        open(reader, processorMapping, null);
    }

    public void open(Reader reader, Map<String, CellProcessor> processorMapping, Map<String, String> fieldNameMapping)
            throws IOException {
        if (null == this.csvReader) {
            this.csvReader = createCsvReader(reader);
            String[] header = csvReader.getHeader(true);
            this.fieldNames = getFieldNames(header, fieldNameMapping);
            this.processors = getProcessors(header, processorMapping);
        } else {
            throw new IllegalStateException("CsvImporter is already open");
        }
    }

    @Override
    public void close() throws IOException {
        if (null != csvReader) {
            csvReader.close();
            csvReader = null;
            fieldNames = null;
            processors = null;
        }
    }

    public int getRowNumber() {
        if (null != csvReader) {
            return csvReader.getRowNumber();
        } else {
            return -1;
        }
    }

    public boolean isOpen() {
        return null != csvReader;
    }

    protected R getCsvReader() {
        return csvReader;
    }

    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    protected String[] getFieldNames() {
        return fieldNames;
    }

    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    protected CellProcessor[] getProcessors() {
        return processors;
    }

    protected abstract R createCsvReader(Reader reader);

    private CellProcessor[] getProcessors(String[] header, Map<String, CellProcessor> processorMapping) {
        List<CellProcessor> processorsList = new ArrayList<>(header.length);
        for (String column : header) {
            if (processorMapping.containsKey(column)) {
                processorsList.add(processorMapping.get(column));
            } else {
                throw new IllegalStateException(String.format("Cell processor for column '%s' not specified", column));
            }
        }

        return processorsList.toArray(new CellProcessor[processorsList.size()]);
    }

    private String[] getFieldNames(String[] header, Map<String, String> fieldNameMapping) {
        if (null != fieldNameMapping) {
            List<String> fieldNamesList = new ArrayList<>(header.length);
            for (String column : header) {
                if (fieldNameMapping.containsKey(column)) {
                    fieldNamesList.add(fieldNameMapping.get(column));
                } else {
                    throw new IllegalStateException(String.format("Field name for column '%s' not specified", column));
                }
            }

            return fieldNamesList.toArray(new String[fieldNamesList.size()]);
        } else {
            return header;
        }
    }
}
