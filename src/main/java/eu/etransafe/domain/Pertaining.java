package eu.etransafe.domain;

import java.util.List;
import java.util.Map;

// Hard coded list of synonyms provided by the eTransafe user group, used for lookup function
public class Pertaining {
    public static final Map<String, List<String>> TERM_PERTAININGS =
            Map.ofEntries(
                    Map.entry("bladder", List.of("vesicular")),
                    Map.entry("bone", List.of("osteal")),
                    Map.entry("brain", List.of("cerebral")),
                    Map.entry("caecum", List.of("intestinal", "enteric")),
                    Map.entry("cns", List.of("cerebral")),
                    Map.entry("colon", List.of("intestinal", "enteric")),
                    Map.entry("derme", List.of("dermal")),
                    Map.entry("duodenum", List.of("intestinal", "enteric")),
                    Map.entry("eye", List.of("ocular")),
                    Map.entry("heart", List.of("cardiac")),
                    Map.entry("ileum", List.of("intestinal", "enteric")),
                    Map.entry("intestine", List.of("intestinal", "enteric")),
                    Map.entry("jejunum", List.of("intestinal", "enteric")),
                    Map.entry("kidney", List.of("renal")),
                    Map.entry("liver", List.of("hepatic")),
                    Map.entry("lung", List.of("pulmonary")),
                    Map.entry("lymph node", List.of("nodal", "lymphatic")),
                    Map.entry("oesophagus", List.of("oesophagal")),
                    Map.entry("ovaries", List.of("ovarian")),
                    Map.entry("pancreas", List.of("pancreatic")),
                    Map.entry("parathyroid", List.of("parathyroidal")),
                    Map.entry("peripheral nerves", List.of("nervous")),
                    Map.entry("skeletal muscle", List.of("muscular")),
                    Map.entry("skin", List.of("cutaneous")),
                    Map.entry("spleen", List.of("splenic")),
                    Map.entry("stomach", List.of("enteric")),
                    Map.entry("testicle", List.of("testicular")),
                    Map.entry("thymus", List.of("thymic")),
                    Map.entry("tongue", List.of("lingual")),
                    Map.entry("urinary", List.of("bladder")),
                    Map.entry("uterus", List.of("uteral")),
                    Map.entry("cardiac", List.of("heart")),
                    Map.entry("cerebral", List.of("brain", "cns")),
                    Map.entry("cutaneous", List.of("skin")),
                    Map.entry("dermal", List.of("derme")),
                    Map.entry("hepatic", List.of("liver")),
                    Map.entry("intestinal", List.of("caecum", "colon", "duodenum", "ileum", "intestine", "jejunum", "enteric")),
                    Map.entry("enteric", List.of("caecum", "colon", "duodenum", "ileum", "intestine", "jejunum", "intestinal", "stomach", "stomachal")),
                    Map.entry("linugal", List.of("tongue")),
                    Map.entry("muscular", List.of("skeletal muscle")),
                    Map.entry("nervous", List.of("peripheral nerves")),
                    Map.entry("nodal", List.of("lymph node", "lymphatic")),
                    Map.entry("lympahtic", List.of("nodal", "lymph node")),
                    Map.entry("ocular", List.of("eye")),
                    Map.entry("oesophagal", List.of("oesophagus")),
                    Map.entry("osteal", List.of("bone")),
                    Map.entry("ovarian", List.of("ovaries")),
                    Map.entry("pancreatic", List.of("pancreas")),
                    Map.entry("parathyroidal", List.of("parathyroid")),
                    Map.entry("pulmonary", List.of("lung")),
                    Map.entry("renal", List.of("kidney")),
                    Map.entry("splenic", List.of("spleen")),
                    Map.entry("stomachal ", List.of("stomach", "enteric")),
                    Map.entry("testicular", List.of("testicle")),
                    Map.entry("thymic", List.of("thymus")),
                    Map.entry("thyroidal", List.of("thyroid")),
                    Map.entry("tracheal", List.of("trachea")),
                    Map.entry("uteral", List.of("uterus")),
                    Map.entry("vesicular", List.of("bladder")));

    private Pertaining() {
    }
}
