package org.opencb.opencga.storage.core.runner;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;
import org.opencb.biodata.formats.variant.vcf4.FullVcfCodec;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantNormalizer;
import org.opencb.biodata.tools.variant.converter.VariantContextToVariantConverter;
import org.opencb.biodata.tools.variant.stats.VariantGlobalStatsCalculator;
import org.opencb.commons.io.DataReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by mh719 on 04/05/16.
 */
public class VcfVariantReader implements DataReader<Variant> {
    protected static Logger logger = LoggerFactory.getLogger(VcfVariantReader.class);

    protected final DataReader<String> reader;
    protected final VCFCodec vcfCodec;
    protected final VariantContextToVariantConverter converter;
    protected final VariantNormalizer normalizer;
    protected final VariantGlobalStatsCalculator variantStatsTask;
    protected final AtomicLong timesOverall = new AtomicLong(0);
//    protected final AtomicLong timeHts = new AtomicLong(0);
//    protected final AtomicLong timeAvro = new AtomicLong(0);
//    protected final AtomicLong timeNorm = new AtomicLong(0);
//    protected final AtomicLong timeStats = new AtomicLong(0);

    public VcfVariantReader(
            DataReader<String> reader, VCFHeader header, VCFHeaderVersion version, VariantContextToVariantConverter
            converter, VariantGlobalStatsCalculator variantStatsTask, VariantNormalizer normalizer) {
        this.vcfCodec = new FullVcfCodec(header, version);
        this.converter = converter;
        this.normalizer = normalizer;
        this.reader = reader;
        this.variantStatsTask = variantStatsTask;

    }

    public VcfVariantReader(
            Path path, VCFHeader header, VCFHeaderVersion version, VariantContextToVariantConverter
            converter, VariantGlobalStatsCalculator variantStatsTask, VariantNormalizer normalizer) {
        this(new StringDataReader(path), header, version, converter, variantStatsTask, normalizer);
    }

    protected List<Variant> processLine(String line) {
//        long curr = System.currentTimeMillis();
        VariantContext htsVar = this.vcfCodec.decode(line);
//        this.timeHts.addAndGet(System.currentTimeMillis() - curr);
//        curr = System.currentTimeMillis();
        Variant variant = this.converter.convert(htsVar);
//        this.timeAvro.addAndGet(System.currentTimeMillis() - curr);
//        curr = System.currentTimeMillis();
        List<Variant> normVar = this.normalizer.apply(Collections.singletonList(variant));
//        this.timeNorm.addAndGet(System.currentTimeMillis() - curr);
//        curr = System.currentTimeMillis();
        this.variantStatsTask.apply(normVar);
//        this.timeStats.addAndGet(System.currentTimeMillis() - curr);
        return normVar;
    }

    private List<Variant> processLines(List<String> lines) {
        return lines.stream().filter(l -> !(l.trim().isEmpty() || l.startsWith("#")))
                .map(l -> processLine(l)).flatMap(l -> l.stream()).collect(Collectors.toList());
    }

    @Override
    public List<Variant> read(int batchSize) {
        long curr = System.currentTimeMillis();
        try {
            return processLines(this.reader.read(batchSize));
        } finally {
            this.timesOverall.addAndGet(System.currentTimeMillis() - curr);
        }
    }

    @Override
    public List<Variant> read() {
        long curr = System.currentTimeMillis();
        try {
            return processLines(this.reader.read());
        } finally {
            this.timesOverall.addAndGet(System.currentTimeMillis() - curr);
        }
    }

    @Override
    public boolean open() {
        return this.reader.open();
    }

    @Override
    public boolean close() {
        return this.reader.close();
    }

    @Override
    public boolean pre() {
        synchronized (variantStatsTask) {
            this.variantStatsTask.pre();
        }
        return this.reader.pre();
    }

    @Override
    public boolean post() {
        this.reader.post();
        synchronized (variantStatsTask) {
            this.variantStatsTask.post();
        }
        logger.info(String.format("Time read: %s", this.timesOverall.get()));
//        logger.info(String.format("Time txt2hts: %s", this.timeHts.get()));
//        logger.info(String.format("Time hts2avro: %s", this.timeAvro.get()));
//        logger.info(String.format("Time avro2norm: %s", this.timeNorm.get()));
//        logger.info(String.format("Time stats: %s", this.timeNorm.get()));
        return true;
    }
}

