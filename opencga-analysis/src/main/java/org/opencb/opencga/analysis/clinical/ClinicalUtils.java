package org.opencb.opencga.analysis.clinical;

import org.apache.commons.collections.CollectionUtils;
import org.opencb.biodata.models.clinical.pedigree.Member;
import org.opencb.biodata.models.clinical.pedigree.Pedigree;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.commons.datastore.core.DataResult;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.utils.ListUtils;
import org.opencb.opencga.catalog.db.api.ProjectDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.core.models.Family;
import org.opencb.opencga.core.models.Individual;
import org.opencb.opencga.core.models.Project;
import org.opencb.opencga.core.results.VariantQueryResult;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ClinicalUtils {

    @Deprecated
    private static File getFile(Path path) {
        File file = null;
        if (path.toFile().exists()) {
            file = path.toFile();
        } else if (Paths.get(path.toString() + ".gz").toFile().exists()) {
            file = Paths.get(path.toString() + ".gz").toFile();
        }

        return file;
    }

    public static void addVariant(VariantQueryResult<Variant> result, Set<String> excludeIds, List<Variant> variants) {
        if (CollectionUtils.isNotEmpty(result.getResults())) {
            for (Variant variant : result.getResults()) {
                if (!excludeIds.contains(variant.getId())) {
                    variants.add(variant);
                }
            }
        }
    }


    // OpenCgaClinicalAnalysis
    public static String getAssembly(CatalogManager catalogManager, String studyId, String sessionId) {
        String assembly = "";
        DataResult<Project> projectQueryResult;
        try {
            projectQueryResult = catalogManager.getProjectManager().get(
                    new Query(ProjectDBAdaptor.QueryParams.STUDY.key(), studyId),
                    new QueryOptions(QueryOptions.INCLUDE, ProjectDBAdaptor.QueryParams.ORGANISM.key()), sessionId);
            if (CollectionUtils.isNotEmpty(projectQueryResult.getResults())) {
                assembly = projectQueryResult.first().getOrganism().getAssembly();
            }
        } catch (CatalogException e) {
            e.printStackTrace();
        }
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(assembly)) {
            assembly = assembly.toLowerCase();
        }
        return assembly;
    }


    public static void removeMembersWithoutSamples(Pedigree pedigree, Family family) {
        Set<String> membersWithoutSamples = new HashSet<>();
        for (Individual member : family.getMembers()) {
            if (ListUtils.isEmpty(member.getSamples())) {
                membersWithoutSamples.add(member.getId());
            }
        }

        Iterator<Member> iterator = pedigree.getMembers().iterator();
        while (iterator.hasNext()) {
            Member member = iterator.next();
            if (membersWithoutSamples.contains(member.getId())) {
                iterator.remove();
            } else {
                if (member.getFather() != null && membersWithoutSamples.contains(member.getFather().getId())) {
                    member.setFather(null);
                }
                if (member.getMother() != null && membersWithoutSamples.contains(member.getMother().getId())) {
                    member.setMother(null);
                }
            }
        }

        if (pedigree.getProband().getFather() != null && membersWithoutSamples.contains(pedigree.getProband().getFather().getId())) {
            pedigree.getProband().setFather(null);
        }
        if (pedigree.getProband().getMother() != null && membersWithoutSamples.contains(pedigree.getProband().getMother().getId())) {
            pedigree.getProband().setMother(null);
        }
    }

}
