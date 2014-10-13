package io.seqware.pancancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sourceforge.seqware.pipeline.workflowV2.AbstractWorkflowDataModel;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;
import net.sourceforge.seqware.pipeline.workflowV2.model.SqwFile;

/**
 * <p>For more information on developing workflows, see the documentation at
 * <a href="http://seqware.github.io/docs/6-pipeline/java-workflows/">SeqWare Java Workflows</a>.</p>
 *
 * Quick reference for the order of methods called:
 * 1. setupDirectory
 * 2. setupFiles
 * 3. setupWorkflow
 * 4. setupEnvironment
 * 5. buildWorkflow
 *
 * See the SeqWare API for
 * <a href="http://seqware.github.io/javadoc/stable/apidocs/net/sourceforge/seqware/pipeline/workflowV2/AbstractWorkflowDataModel.html#setupDirectory%28%29">AbstractWorkflowDataModel</a>
 * for more information.
 */
public class CgpCnIndelSnvStrWorkflow extends AbstractWorkflowDataModel {

  private boolean manualOutput=false;
  private String catPath, echoPath;
  private String greeting ="";
  private static String OUTDIR = "outdir/";
  private static String LOGDIR = "logdir/";

  // MEMORY variables //
  private String  memGnosDownload,
                  // ascat memory
                  memAlleleCount, memAscat, memAscatFinalise,
                  // pindel memory
                  memInputParse, memPindel, memPinVcf, memPinMerge , memPinFlag,
                  // brass memory
                  memBrassInput, memBrassGroup, memBrassFilter, memBrassSplit,
                  memBrassAssemble, memBrassGrass, memBrassTabix,
                  // caveman memory
                  memCavemanSetup, memCavemanSplit, memCavemanSplitConcat,
                  memCavemanMstep, memCavemanMerge, memCavemanEstep,
                  memCavemanMergeResults, memCavemanAddIds, memCavemanFlag
          ;

  // workflow variables
  private String  // reference variables
                  species, assembly,
                  // sequencing type/protocol
                  seqType, seqProtocol,
                  //GNOS identifiers
                  tumourAnalysisId, controlAnalysisId,
                  // test files, instead of GNOS ids
                  tumourBam, normalBam,
                  // ascat variables
                  gender,
                  // pindel variables
                  refExclude,
                  // caveman variables
                  httpRangeSrv,
                  //general variables
                  installBase, refBase;

  private int pindelInputThreads, coresAddressable;
  
  private void init() {
    try {
      //optional properties
      if (hasPropertyAndNotNull("manual_output")) {
        manualOutput = Boolean.valueOf(getProperty("manual_output"));
      }
      if (hasPropertyAndNotNull("greeting")) {
        greeting = getProperty("greeting");
      }
      //these two properties are essential to the workflow. If they are null or do not
      //exist in the INI, the workflow should exit.
      catPath = getProperty("cat");
      echoPath = getProperty("echo");
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setupDirectory() {
    //since setupDirectory is the first method run, we use it to initialize variables too.
    init();
    // creates a dir1 directory in the current working directory where the workflow runs
    this.addDirectory(OUTDIR);
    this.addDirectory(LOGDIR);
  }

  @Override
  public Map<String, SqwFile> setupFiles() {
    try {
      coresAddressable = Integer.valueOf(getProperty("coresAddressable"));

      // MEMORY //
      memGnosDownload = getProperty("memGnosDownload");
      memAlleleCount = getProperty("memAlleleCount");
      memAscat = getProperty("memAscat");
      memAscatFinalise = getProperty("memAscatFinalise");
      memInputParse = getProperty("memInputParse");
      memPindel = getProperty("memPindel");
      memPinVcf = getProperty("memPinVcf");
      memPinMerge = getProperty("memPinMerge");
      memPinFlag = getProperty("memPinFlag");
      memBrassInput = getProperty("memBrassInput");
      memBrassGroup = getProperty("memBrassGroup");
      memBrassFilter = getProperty("memBrassFilter");
      memBrassSplit = getProperty("memBrassSplit");
      memBrassAssemble = getProperty("memBrassAssemble");
      memBrassGrass = getProperty("memBrassGrass");
      memBrassTabix = getProperty("memBrassTabix");
      
      memCavemanSetup = getProperty("memCavemanSetup");
      memCavemanSplit = getProperty("memCavemanSplit");
      memCavemanSplitConcat = getProperty("memCavemanSplitConcat");
      memCavemanMstep = getProperty("memCavemanMstep");
      memCavemanMerge = getProperty("memCavemanMerge");
      memCavemanEstep = getProperty("memCavemanEstep");
      memCavemanMergeResults = getProperty("memCavemanMergeResults");
      memCavemanAddIds = getProperty("memCavemanAddIds");
      memCavemanFlag = getProperty("memCavemanFlag");

      // REFERENCE INFO //
      species = getProperty("species");
      assembly = getProperty("assembly");
      refBase = getProperty("refBase");
      
      // Sequencing info
      seqType = getProperty("seqType");
      seqProtocol = getProperty("seqProtocol");

      // Specific to ASCAT workflow //
      gender = getProperty("gender");
      
      // Specific to PINDEL workflow //
      pindelInputThreads = Integer.valueOf(getProperty("pindelInputThreads"));
      
      // Specific to Caveman workflow //
      httpRangeSrv = getProperty("httpRangeSrv");

      // Which data to process //
      if(hasPropertyAndNotNull("tumourAnalysisId") && hasPropertyAndNotNull("controlAnalysisId")) {
        // used in preference to test files if set //
        tumourAnalysisId = getProperty("tumourAnalysisId");
        controlAnalysisId = getProperty("controlAnalysisId");
      }

      // pindel specific
      refExclude = getProperty("refExclude");

      // test files
      if(tumourAnalysisId == null || tumourAnalysisId.equals("CHANGEME")) {
        tumourBam = getProperty("tumourBam");
      }
      if(controlAnalysisId == null || controlAnalysisId.equals("CHANGEME")) {
        normalBam = getProperty("normalBam");
      }

      //environment
      installBase = getProperty("installBase");
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new RuntimeException(ex);
    }
    return this.getFiles();
  }

  @Override
  public void buildWorkflow() {
    // First we need the tumour and normal BAM files (+bai)
    // this can be done in parallel, based on tumour/control
    // correlate names on by number of parallel jobs neeeded.
    String samples[] = {"tumour", "control"};

    
    Job[] gnosDownloadJobs = new Job[2];
    /*
    // @TODO, when we have a decider in place
    for(int i=0; i<2; i++) {
      String thisId = "";
      switch(i){
        case 0: thisId = tumourAnalysisId;
        case 1: thisId = controlAnalysisId;
      }

      Job gnosDownload = this.getWorkflow().createBashJob("GNOSDownload");
      gnosDownload.setMaxMemory(memGnosDownload);
      gnosDownload.getCommand()
                    .addArgument(this.getWorkflowBaseDir()+"/bin/download_gnos.pl") // ?? @TODO Is there a generic script for this???
                    .addArgument(thisId); // @TODO can't use Donor ID as a donor can have multiple tumours (but only one normal)
      // the file needs to end up in tumourBam/normalBam
      gnosDownloadJobs[i] = gnosDownload;
    }*/
    
    /**
     * ASCAT - Copynumber
     * Depends on
     *  - tumour/normal BAMs
     *  - Gender, will attempt to determine if not specified
     */
    Job[] alleleCountJobs = new Job[2];
    for(int i=0; i<2; i++) {
      Job alleleCountJob = this.cgpAscatBaseJob("ascatAlleleCount", "allele_count", i+1);
      alleleCountJob.setMaxMemory(memAlleleCount);
//      alleleCountJob.addParent(gnosDownloadJobs[0]);
//      alleleCountJob.addParent(gnosDownloadJobs[1]);
      alleleCountJobs[i] = alleleCountJob;
    }

    Job ascatJob = this.cgpAscatBaseJob("ascat", "ascat", 1);
    ascatJob.setMaxMemory(memAscat);
    ascatJob.addParent(alleleCountJobs[0]);
    ascatJob.addParent(alleleCountJobs[1]);
    
    Job ascatFinaliseJob = this.cgpAscatBaseJob("ascatFinalise", "finalise", 1);
    ascatFinaliseJob.setMaxMemory(memAscatFinalise);
    ascatFinaliseJob.addParent(ascatJob);
    
    /**
     * Pindel - InDel calling
     * Depends on:
     *  - tumour/normal BAMs
     */
    Job[] pindelInputJobs = new Job[2];
    for(int i=0; i<2; i++) {
      Job inputParse = this.pindelBaseJob("pindelInput", "input", i+1);
      inputParse.getCommand().addArgument("-c " + pindelInputThreads);
      inputParse.setMaxMemory(memInputParse);
      inputParse.setThreads(pindelInputThreads);
//      inputParse.addParent(gnosDownloadJobs[0]);
//      inputParse.addParent(gnosDownloadJobs[1]);  
      pindelInputJobs[i] = inputParse;
    }
    
    // determine number of refs to process
    // we know that this is static for PanCancer so be lazy 24 jobs (1-22,X,Y)
    // but pindel needs to know the exclude list so hard code this
    Job pinVcfJobs[] = new Job[24];
    for(int i=0; i<24; i++) {
      Job pindelJob = this.pindelBaseJob("pindelPindel", "pindel", i+1);
      pindelJob.setMaxMemory(memPindel);
      pindelJob.addParent(pindelInputJobs[0]);
      pindelJob.addParent(pindelInputJobs[1]);
      
      Job pinVcfJob = this.pindelBaseJob("pindelVcf", "pin2vcf", i+1);
      pinVcfJob.setMaxMemory(memPinVcf);
      pinVcfJob.addParent(pindelJob);
      
      // pinVcf depends on pindelJob so only need have dependency on the pinVcf
      pinVcfJobs[i] = pinVcfJob;
    }
    
    Job pindelMergeJob = this.pindelBaseJob("pindelMerge", "merge", 1);
    pindelMergeJob.setMaxMemory(memPinMerge);
    for (Job parent : pinVcfJobs) {
      pindelMergeJob.addParent(parent);
    }
    
    Job pindelFlagJob = this.pindelBaseJob("pindelFlag", "flag", 1);
    pindelFlagJob.setMaxMemory(memPinFlag);
    pindelFlagJob.addParent(pindelMergeJob);
    
    /**
     * BRASS - BReakpoint AnalySiS
     * Depends on:
     *  - tumour/normal BAMs
     *  - ASCAT output at filter step
     */
    Job brassInputJobs[] = new Job[2];
    for(int i=0; i<2; i++) {
      Job brassInputJob = this.brassBaseJob("brassInput", "input", i+1);
      brassInputJob.setMaxMemory(memBrassInput);
//      brassInputJob.addParent(gnosDownloadJobs[0]);
//      brassInputJob.addParent(gnosDownloadJobs[1]);
      brassInputJobs[i] = brassInputJob;
    }
    
    Job brassGroupJob = this.brassBaseJob("brassGroup", "group", 1);
    brassGroupJob.setMaxMemory(memBrassGroup);
    brassGroupJob.addParent(brassInputJobs[0]);
    brassGroupJob.addParent(brassInputJobs[1]);
    
    Job brassFilterJob = this.brassBaseJob("brassFilter", "filter", 1);
    brassFilterJob.setMaxMemory(memBrassFilter);
    brassFilterJob.addParent(brassGroupJob);
    brassFilterJob.addParent(ascatFinaliseJob); // NOTE: dependency on ASCAT!!
    
    Job brassSplitJob = this.brassBaseJob("brassSplit", "split", 1);
    brassSplitJob.setMaxMemory(memBrassSplit);
    brassSplitJob.addParent(brassFilterJob);
    
    Job brassAssembleJob = this.brassBaseJob("brassAssemble", "assemble", 1);
    brassAssembleJob.getCommand().addArgument("-l 1"); // regardless of number of splits, run sequentially
    brassAssembleJob.setMaxMemory(memBrassAssemble);
    brassAssembleJob.addParent(brassSplitJob);
    
    Job brassGrassJob = this.brassBaseJob("brassGrass", "grass", 1);
    brassGrassJob.setMaxMemory(memBrassGrass);
    brassGrassJob.addParent(brassAssembleJob);
    
    Job brassTabixJob = this.brassBaseJob("brassTabix", "tabix", 1);
    brassTabixJob.setMaxMemory(memBrassTabix);
    brassTabixJob.addParent(brassGrassJob);
    
    /**
     * CaVEMan - SNV analysis
     * Depends on:
     *  - tumour/normal BAMs
     *  - ASCAT from outset
     *  - pindel at flag step
     */
    Job cavemanSetupJob = this.cavemanBaseJob("cavemanSetup", "setup", 1);
    cavemanSetupJob.setMaxMemory(memCavemanSetup);
    cavemanSetupJob.addParent(gnosDownloadJobs[0]); // not really needed as ASCAT will do this check
    cavemanSetupJob.addParent(gnosDownloadJobs[1]); // but here for complete disclosure
    cavemanSetupJob.addParent(ascatJob);
    
    // should really line count the fai file
    Job cavemanSplitJobs[] = new Job[86];
    for(int i=0; i<86; i++) {
      Job cavemanSplitJob = this.cavemanBaseJob("cavemanSplit", "split", i+1);
      cavemanSplitJob.setMaxMemory(memCavemanSplit);
      cavemanSplitJob.addParent(cavemanSetupJob);
      cavemanSplitJobs[i] = cavemanSplitJob;
    }
    
    Job cavemanSplitConcatJob = this.cavemanBaseJob("cavemanSplitConcat", "split_concat", 1);
    cavemanSplitConcatJob.setMaxMemory(memCavemanSplitConcat);
    for (Job cavemanSplitJob : cavemanSplitJobs) {
      cavemanSplitConcatJob.addParent(cavemanSplitJob);
    }
    
    List<Job> cavemanMstepJobs = new ArrayList<Job>();
    for(int i=0; i<coresAddressable; i++) {
      Job cavemanMstepJob = this.cavemanBaseJob("cavemanMstep", "mstep", i+1);
      cavemanMstepJob.getCommand().addArgument("-l " + coresAddressable);
      cavemanMstepJob.setMaxMemory(memCavemanMstep);
      cavemanMstepJob.addParent(cavemanSplitConcatJob);
      cavemanMstepJobs.add(cavemanMstepJob);
    }
    
    Job cavemanMergeJob = this.cavemanBaseJob("cavemanMerge", "merge", 1);
    cavemanMergeJob.setMaxMemory(memCavemanMerge);
    for(Job parent : cavemanMstepJobs) {
      cavemanMergeJob.addParent(parent);
    }
    
    List<Job> cavemanEstepJobs = new ArrayList<Job>();
    for(int i=0; i<coresAddressable; i++) {
      Job cavemanEstepJob = this.cavemanBaseJob("cavemanEstep", "estep", i+1);
      cavemanEstepJob.getCommand().addArgument("-l " + coresAddressable);
      cavemanEstepJob.setMaxMemory(memCavemanEstep);
      cavemanEstepJob.addParent(cavemanMergeJob);
      cavemanEstepJobs.add(cavemanEstepJob);
    }
    
    Job cavemanMergeResultsJob = this.cavemanBaseJob("cavemanMergeResults", "merge_results", 1);
    cavemanMergeResultsJob.setMaxMemory(memCavemanMergeResults);
    for(Job parent : cavemanEstepJobs) {
      cavemanMergeResultsJob.addParent(parent);
    }
    
    Job cavemanAddIdsJob = this.cavemanBaseJob("cavemanAddIds", "add_ids", 1);
    cavemanAddIdsJob.setMaxMemory(memCavemanAddIds);
    cavemanAddIdsJob.addParent(cavemanMergeResultsJob);
    
    Job cavemanFlagJob = this.cavemanBaseJob("cavemanFlag", "flag", 1);
    cavemanFlagJob.getCommand().addArgument("-in " + OUTDIR + "/pindel/germline.bed"); // @TODO
    cavemanFlagJob.setMaxMemory(memCavemanFlag);
    cavemanFlagJob.addParent(cavemanAddIdsJob);
    
    
    
    

    // @TODO then we need to write back to GNOS

  }
  
  private Job cavemanBaseJob(String name, String process, int index) {
    Job thisJob = this.getWorkflow().createBashJob(name);
    thisJob.getCommand()
              .addArgument(this.getWorkflowBaseDir()+ "/bin/wrapper.sh")
              .addArgument(installBase)
              .addArgument(LOGDIR.concat(process).concat(".").concat(Integer.toString(index)).concat(".log"))
              .addArgument("caveman.pl")
              .addArgument("-p " + process)
              .addArgument("-i " + index)
            
              .addArgument("-r " + refBase + "/genome.fa.fai")
              .addArgument("-ig " + refBase + "/caveman/ucscHiDepth_0.01_merge1000_no_exon.tsv")
              .addArgument("-b " + refBase + "/caveman")
              .addArgument("-u " + httpRangeSrv + "/caveman_unmatched")
              .addArgument("-np " + seqType)
              .addArgument("-tp " + seqType)
              .addArgument("-sa " + assembly)
              .addArgument("-s " + species)
              .addArgument("-st " + seqProtocol)
            
              .addArgument("-o " + OUTDIR + "/caveman")
              .addArgument("-tb " + tumourBam)
              .addArgument("-nb " + normalBam)
              .addArgument("-tc " + OUTDIR + "/ascat/tum.cn.bed") // @TODO
              .addArgument("-nc " + OUTDIR + "/ascat/norm.cn.bed") // @TODO
              .addArgument("-k " + OUTDIR + "/ascat/samplestatistics.csv") // @TODO
            ;

    return thisJob;
  }

  private Job cgpAscatBaseJob(String name, String process, int index) {
    Job thisJob = this.getWorkflow().createBashJob(name);
    thisJob.getCommand()
              .addArgument(this.getWorkflowBaseDir()+ "/bin/wrapper.sh")
              .addArgument(installBase)
              .addArgument(LOGDIR.concat(process).concat(".").concat(Integer.toString(index)).concat(".log"))
              .addArgument("ascat.pl")
              .addArgument("-p " + process)
              .addArgument("-i " + index)
              .addArgument("-r " + refBase + "/genome.fa")
              .addArgument("-s " + refBase + "/ascat/SnpLocus.tsv")
              .addArgument("-sp " + refBase + "/ascat/SnpPositions.tsv")
              .addArgument("-sg " + refBase + "/ascat/SnpGcCorrections.tsv")
              .addArgument("-pr " + seqType)
              .addArgument("-ra " + assembly)
              .addArgument("-rs " + species)
            //.addArgument("-pl " + "ILLUMINA") // should be in BAM header
              .addArgument("-o " + OUTDIR + "/ascat")
              .addArgument("-t " + tumourBam)
              .addArgument("-n " + normalBam)
              ;
    // this is used when gender is not specified
    if(gender.equals("L")) {
      thisJob.getCommand().addArgument("-l Y:2654896-2655740");
    }
    thisJob.getCommand().addArgument("-g " + gender);

    return thisJob;
  }

  private Job pindelBaseJob(String name, String process, int index) {
    Job thisJob = this.getWorkflow().createBashJob(name);
    thisJob.getCommand()
              .addArgument(this.getWorkflowBaseDir()+ "/bin/wrapper.sh")
              .addArgument(installBase)
              .addArgument(LOGDIR.concat(process).concat(".").concat(Integer.toString(index)).concat(".log"))
              .addArgument("pindel.pl")
              .addArgument("-p " + process)
              .addArgument("-i " + index)
              .addArgument("-r " + refBase + "/genome.fa")
              .addArgument("-e " + refExclude)
              .addArgument("-st " + seqType)
              .addArgument("-as " + assembly)
              .addArgument("-sp " + species)
              .addArgument("-s " + refBase + "/pindel/simpleRepeats.bed.gz")
              .addArgument("-f " + refBase + "/pindel/genomicRules.lst")
              .addArgument("-g " + refBase + "/vagrent/e74/Human.GRCh37.codingexon_regions.indel.bed.gz")
              .addArgument("-u " + refBase + "/pindel/pindel_np.gff3.gz")
              .addArgument("-sf " + refBase + "/pindel/softRules.lst")
              .addArgument("-b " + refBase + "/pindel/ucscHiDepth_0.01_mrg1000_no_exon_coreChrs.bed.gz")
              .addArgument("-o " + OUTDIR + "/pindel")
              .addArgument("-t " + tumourBam)
              .addArgument("-n " + normalBam)
              ;
    return thisJob;
  }

  private Job brassBaseJob(String name, String process, int index) {
    Job thisJob = this.getWorkflow().createBashJob(name);
    thisJob.getCommand()
              .addArgument(this.getWorkflowBaseDir()+ "/bin/wrapper.sh")
              .addArgument(installBase)
              .addArgument(LOGDIR.concat(process).concat(".").concat(Integer.toString(index)).concat(".log"))
              .addArgument("brass.pl")
              .addArgument("-p " + process)
              .addArgument("-i " + index)
              .addArgument("-r " + refBase + "/genome.fa")
              .addArgument("-e " + refExclude)
              .addArgument("-pr " + seqType)
              .addArgument("-as " + assembly)
              .addArgument("-s " + species)
              //.addArgument("-pl " + "ILLUMINA") // should be in BAM header
              .addArgument("-d "  + refBase + "/brass/ucscHiDepth_0.01_mrg1000_no_exon_coreChrs.bed.gz")
              .addArgument("-r "  + refBase + "/brass/brassRepeats.bed.gz")
              .addArgument("-f "  + refBase + "/brass/brass_np.groups.gz")
              .addArgument("-g_cache "  + refBase + "/vagrent/e74/Homo_sapiens.GRCh37.74.vagrent.cache.gz")
              .addArgument("-o " + OUTDIR + "/brass")
              .addArgument("-t " + tumourBam)
              .addArgument("-n " + normalBam)
              .addArgument("-a " + OUTDIR + "/ascat/") // @TODO
            ;
    return thisJob;
  }

}