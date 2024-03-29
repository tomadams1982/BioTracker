/*
 * TODO LIST:
 * - Vertical migration/behaviour
 * - Parallelisation?
 *      + Threads using fork/join 
 *        http://www.oracle.com/technetwork/articles/java/fork-join-422606.html
 *        http://tutorials.jenkov.com/java-util-concurrent/java-fork-and-join-forkjoinpool.html
 *        (16 cores per cluster node)
 *      + MPI e.g. MPJ?
 */
package com.scottishseafarms.particle_track;

import java.io.*;
import java.util.*;
import java.util.stream.IntStream;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;

import java.util.concurrent.ExecutionException;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
 
//import java.awt.geom.Path2D;

//import extUtils.*;

/**
 *
 * @author tomdude
 */
public class Particle_track {
   
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        // TODO code application logic here

        System.out.println("Starting particle tracking program\n");
        Date date = new Date();
        // display time and date using toString()
        System.out.println(date.toString());

        long heapMaxSize = Runtime.getRuntime().maxMemory();
        System.out.println("Max heap " + heapMaxSize);

        //System.out.println(new Date().toString());
        long startTime = System.currentTimeMillis();

        //RanMT ran = new RanMT(System.currentTimeMillis());
        System.out.println("Reading in data\n");

        System.out.println(System.getProperty("user.dir"));
        
        if (args.length != 1)
        {
            System.err.println("Must specify single argument: name of .properties file");
            System.exit(1);
        }
        //RunProperties rp = new RunProperties("model_setup.properties");
        RunProperties rp = new RunProperties(args[0]); // first (and only?) cmd line arg is properties filename e.g. model_setup.properties
        // Use this instead of previous to create runProps from CMD line args
        //RunProperties runProps = new RunProperties(args); 

        int[] startDate = ISO_datestr.dateIntParse(rp.start_ymd);
        ISO_datestr currentIsoDate = new ISO_datestr(startDate[0], startDate[1], startDate[2]);
        int[] endDate = ISO_datestr.dateIntParse(rp.end_ymd);
        ISO_datestr endIsoDate = new ISO_datestr(endDate[0], endDate[1], endDate[2]);

        int numberOfDays = endIsoDate.getDateNum() - currentIsoDate.getDateNum() + 1;

        // Print all main arguments
        System.out.printf("-----------------------------------------------------------\n");
        System.out.printf("Location           = %s\n", rp.location);
        System.out.printf("Habitat            = %s\n", rp.habitat);
        System.out.printf("N_parts/site       = %d\n", rp.nparts);
        System.out.printf("hydromod dt (s)    = %f\n", rp.dt);
        System.out.printf("hydromod rec/file  = %d\n", rp.recordsPerFile1);
        System.out.printf("stepsperstep       = %d\n", rp.stepsPerStep);
        System.out.printf("firstfile          = %d\n", rp.start_ymd);
        System.out.printf("lastfile           = %d\n", rp.end_ymd);
        System.out.printf("Simulated dur. (d) = %f\n", (double) numberOfDays);
        System.out.printf("Simulated dur. (s) = %f\n", (double) numberOfDays * 86400);
        //System.out.printf("Simulated dur. (s) = %f\n",rp.dt*rp.recordsPerFile1*(rp.lastday-rp.firstday+1));
        System.out.printf("RK4                = %s\n", rp.rk4);
        System.out.printf("Vertical behaviour = %d\n", rp.behaviour);
        System.out.printf("Viable time (h)    = %f\n", rp.viabletime);
        System.out.printf("Viable time (d)    = %f\n", rp.viabletime / 24.0);
        System.out.printf("Threshold distance = %d\n", rp.thresh);
        System.out.printf("Diffusion D_h      = %f (diffusion: %s)\n", rp.D_h, rp.diffusion);
        System.out.printf("Coord ref          = %s\n", rp.coordRef);
        System.out.printf("-----------------------------------------------------------\n");

        // --------------------------------------------------------------------------------------
        // File reading and domain configuration
        // --------------------------------------------------------------------------------------       
        List<Mesh> meshes = new ArrayList<>();
        meshes.add(new Mesh(rp.mesh1,rp.mesh1Type,rp.coordRef));
        if (rp.mesh2.equals("") != true)
        {
            meshes.add(new Mesh(rp.mesh2,rp.mesh2Type,rp.coordRef));
        }


        int[] allelems = IntStream.rangeClosed(0, meshes.get(0).getUvnode()[0].length-1).toArray();
        
        double subStepDt = rp.dt / (double) rp.stepsPerStep; // number of seconds per substep
        double dev_perstep = Math.pow(0.1, subStepDt);
        System.out.println("Particle subStepDt = " + subStepDt + " dev_perstep = " + dev_perstep);
        System.out.println("behaviour = " + rp.behaviour);

        // --------------------------------------------------------------------------------------
        // Creating initial particle array
        // --------------------------------------------------------------------------------------
        // load array of start node IDs (as stored by matlab)
        //double startlocs[][] = new double[10][3];       
        //double endlocs[][] = new double[10][3];
        
        // A new way of creating habitat sites, allowing use of more information
        List<HabitatSite> habitat = new ArrayList<>();
        System.out.println("Creating start sites");
        habitat = IOUtils.createHabitatSites(rp.sitefile, null, 4, false, meshes, rp);
//        for (HabitatSite site : habitat)
//        {
//            System.out.println(site.toString());
//        }
        
        FileWriter fstream = new FileWriter("startSitesUsed.dat",false);
        PrintWriter out = new PrintWriter(fstream);
        for (int s = 0; s < habitat.size(); s++)
        {
            out.println(habitat.get(s).toString());
        }
        out.close();
        
//        // Record the names for reference later when calculating psteps
        List<String> siteNames = new ArrayList<>();
        for (int h = 0; h <  habitat.size(); h++)
        {
            siteNames.add(habitat.get(h).getID());
            //System.out.println(siteNames.get(h));
        }
        //System.out.println("list indices: "+siteNames.indexOf("AIRD2") + ", " + siteNames.indexOf("AIRD6"));
        
        
        
        
        // Need a list of end sites - have just used the same list for now
        List<HabitatSite> habitatEnd = new ArrayList<>();
        System.out.println("Creating end sites");
        habitatEnd = IOUtils.createHabitatSites(rp.sitefileEnd, null, 4, false, meshes, rp);
        
        //int nparts_per_site = rp.nparts;
        //int nTracksSavedPerSite = Math.min(1, nparts_per_site);
        //int nparts = rp.nparts * startlocs.length;
        int nparts = rp.nparts * habitat.size();

//        for (int i = 0; i < startlocs.length; i++) {
//            startlocs[i][0]--;
//            //System.out.println(startlocs[i][0]+" "+startlocs[i][1]+" "+startlocs[i][2]);
//        }

        // --------------------------------------------------------------------------------------
        // Setup particles
        // --------------------------------------------------------------------------------------
        List<Particle> particles = new ArrayList<>(nparts);
        int numParticlesCreated = 0; // Counter to keep track of how many particles have been created
        boolean allowRelease = true; // boolean to be switched after a single release event
        // --------------------------------------------------------------------------------------
        // Read in particles to be restarted, if there are any
        // --------------------------------------------------------------------------------------
        if (!rp.restartParticles.equalsIgnoreCase(""))
        {
            List<Particle> restartParts = IOUtils.readRestartParticles(rp);
            particles.addAll(restartParts);
            numParticlesCreated = numParticlesCreated+(restartParts.size());
            System.out.println("numberOfParticles: "+numParticlesCreated+" "+particles.size());
        }
        
        // --------------------------------------------------------------------------------------
        // Setup hydrodynamic fields and file lists.
        // TODO: At present this holds only one file per mesh at this point; is there a way to get the whole list of files in the date range, and check presence before starting?
        // --------------------------------------------------------------------------------------
        List<HydroField> hydroFields = new ArrayList<>();
//        List<List<File>> fileList = new ArrayList<>();
        String[] varNames = new String[]{"","","","",""};
        
        // --------------------------------------------------------------------------------------
        // Set up times at which to print particle locations to file 
        // --------------------------------------------------------------------------------------
        int simLengthHours = numberOfDays * 24;
        System.out.println("simLengthHours " + simLengthHours);
       
        // --------------------------------------------------------------------------------------
        // Final setup bits
        // --------------------------------------------------------------------------------------
        System.out.println("Starting time loop");
        int[] searchCounts = new int[5];
        double minMaxDistTrav[] = new double[2];
        minMaxDistTrav[0] = 10000000;
        minMaxDistTrav[1] = 0;

        int stepcount = 0;
        int calcCount = 0;
        double time = 0; // time is updataed in HOURS as the simulation progresses
        int printCount = 0;

        int[] freeViableSettleExit = new int[4];

        //int numberOfExecutorThreads = Runtime.getRuntime().availableProcessors();
        int numberOfExecutorThreads = rp.parallelThreads;
        if (rp.parallel == false) {
            numberOfExecutorThreads = 1;
        }
        //numberOfExecutorThreads = 1;
        System.out.println("Number of executor threads = " + numberOfExecutorThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfExecutorThreads);
        CompletionService<List<Particle>> executorCompletionService = new ExecutorCompletionService<List<Particle>>(executorService);                           
        
        //final Collection<Callable<List<Particle>>> callables = new ArrayList<>();
        final Collection<Callable<List<Particle>>> callables = new ArrayList<Callable<List<Particle>>>();

        //String locationHeader = "hour ID startDate age startLocation x y elem status density mesh";
        String particleRestartHeader = "hour ID startDate age startLocation x y elem status density mesh depth degreeDays";
        String arrivalHeader = "ID startDate startTime startLocation endDate endTime endLocation age density";
        
        // Set up arrays to hold particle density*hour counts
        int pstepsInd2 = 2;
        if (rp.splitPsteps==true)
        {
            pstepsInd2 = habitat.size();
        }
        float[][] pstepsImmature = new float[meshes.get(0).getNElems()][pstepsInd2 ];
        float[][] pstepsMature = new float[meshes.get(0).getNElems()][pstepsInd2];
        
        //List<SparseFloatArray> pstepsImmature = new ArrayList<>(habitat.size());
        //List<SparseFloatArray> pstepsMature = new ArrayList<>(habitat.size());
        
        // Set up array to hold connectivity counts
        
        float[][] connectivity = new float[habitat.size()][habitat.size()];

        try {
            // --------------------------------------------------------------------------------------
            // Start time loop
            // --------------------------------------------------------------------------------------
            long currTime = System.currentTimeMillis();
            //for (int fnum = rp.firstday; fnum <= rp.lastday; fnum++)
            for (int fnum = 0; fnum < numberOfDays; fnum++) {

                // ******* Is it the last day of the simulation? *******
                // If so, readHydroFields will read the last day file twice, and use the first hour of endDay as hour1
                // of endDay+1 for interpolation purposes.
                // Other design choices possible:
                // - stop particles at hour 23 on last day (introduces and hour gap if using for restart)
                // - do no interpolation for last hour of run
                // Whatever you choose - a small error would be introduced. Could do using actual next day file but this
                // means losing a day's worth of hydrodynamic data if running in operational mode.
                boolean isLastDay = false;
                if (fnum == numberOfDays-1 && rp.duplicateLastDay == true){
                    isLastDay = true;
                }
                        
                String today = currentIsoDate.getDateStr();
                System.out.println(today);
                //IOUtils.printFileHeader(locationHeader,"locations_" + today + ".dat");
                if (rp.recordLocations == true)
                {
                    IOUtils.printFileHeader(particleRestartHeader,"locations_" + today + ".dat");
                }
                if (rp.recordArrivals == true)
                {
                    IOUtils.printFileHeader(arrivalHeader,"arrivals_" + today + ".dat");
                }
                
                long splitTime = System.currentTimeMillis();
                System.out.printf("\n------ Day %d (%s) - Stepcount %d (%f hrs) ------ \n",
                        fnum + 1, currentIsoDate.getDateStr(), stepcount, time);
                System.out.println("Elapsed time (s) = " + (splitTime - startTime) / 1000.0);
                System.out.println("Last 24hr time (s) = " + (splitTime - currTime) / 1000.0);
                currTime = System.currentTimeMillis();
                
                // set an initial tide state
                String tideState = "flood";

                // COUNT the number of particles in different states
                freeViableSettleExit = particleCounts(particles);
                System.out.println("Free particles    = " + freeViableSettleExit[0]);
                System.out.println("Viable particles  = " + freeViableSettleExit[1]);
                System.out.println("Arrival count     = " + freeViableSettleExit[2]);
                System.out.println("Boundary exits    = " + freeViableSettleExit[3]);

                
                
                
                // default, run loop forwards
                // ---- LOOP OVER ENTRIES IN THE HYDRO OUTPUT ------------------------
                for (int tt = 0; tt < 24; tt++) {

                    System.out.printf("--------- HOUR %d ----------\n",tt);
                    // Calculate current time of the day (complete hours elapsed since midnight)
                    int currentHour = tt;
                    //System.out.printf("%d \n", tt + 1);
                    
                    // Read new hydrodynamic fields?
                    boolean readNewFields = true;
                    if (tt != 0)
                    {
                        readNewFields = false;
                    }
                    if (readNewFields == true)
                    {
                        // Get new hydro fields
                        hydroFields.clear();
                        hydroFields = readHydroFields(meshes,currentIsoDate,tt,isLastDay,rp);
                    }
                                       
                    // Create new particles, if releases are scheduled hourly, or if release is scheduled for this
                    // exact hour
                    if (rp.releaseScenario==1 || (rp.releaseScenario==0 && time>=rp.releaseTime && allowRelease==true)
                            || (rp.releaseScenario==2 && time>=rp.releaseTime && time<=rp.releaseTimeEnd))
                    {
                        System.out.printf("Release attempt: releaseScenario %d, releaseTime %f, allowRelease %s newParticlesCreatedBeforeNow %d \n",
                            rp.releaseScenario,time,allowRelease,numParticlesCreated);
                        //System.out.printf("releaseScenario==1, releasing hourly (hour = %d)\n",currentHour);
                        List<Particle> newParts = createNewParticles(habitat,meshes,
                                rp,currentIsoDate,currentHour,numParticlesCreated);
                        particles.addAll(newParts);
                        numParticlesCreated = numParticlesCreated+(rp.nparts*habitat.size());
                        System.out.println("numberOfParticles: "+numParticlesCreated+" "+particles.size());
                        // If only one release to be made, prevent further releases
                        if (rp.releaseScenario==0)
                        {
                            allowRelease = false;
                        }
                    }

                    // ---- INTERPOLATE BETWEEN ENTRIES IN THE HYDRO OUTPUT ------------------------
                    for (int st = 0; st < rp.stepsPerStep; st++) {

                        // Update the element count arrays
                        //pstepUpdater(particles, rp, pstepsMature, pstepsImmature, subStepDt);

                        //System.out.print(",");
                        //System.out.println("nfreeparts = "+nfreeparts);
                        // MOVE the particles
                        if (rp.parallel == true) {
                            int particlesSize = particles.size();
                            int listStep = particlesSize / numberOfExecutorThreads;
                            for (int i = 0; i < numberOfExecutorThreads; i++) {
                                List<Particle> subList;
                                if(i==numberOfExecutorThreads-1){
                                    // Note: ArrayList.subList(a,b) is inclusive of a but exclusive of b => 
                                    subList = particles.subList(i * listStep, particlesSize);
                                    //System.out.println(listStep+" "+i+" "+(i*listStep)+" "+(particlesSize-1));
                                }else{
                                    subList = particles.subList(i * listStep, (i + 1) * listStep);
                                    //System.out.println(listStep+" "+i+" "+(i*listStep)+" "+((i + 1) * listStep - 1));
                                }
                                callables.add(new ParallelParticleMover(subList, time, tt, st, subStepDt, rp,
                                        meshes, hydroFields, habitatEnd, allelems, 
                                        searchCounts,
                                        minMaxDistTrav));

                            }
                            for (Callable<List<Particle>> callable : callables) {
                                executorCompletionService.submit(callable);
                            }
                            for (Callable<List<Particle>> callable : callables) {
                                executorCompletionService.take().get();
                            }
                            callables.clear();
                        } else {
                            // Normal serial loop
                            for (Particle part : particles) {
                                ParallelParticleMover.move(part, time, tt, st, subStepDt, rp,
                                        meshes, 
                                        hydroFields, 
                                        habitatEnd,
                                        allelems,
                                        searchCounts,
                                        minMaxDistTrav);

                            }
                        }

                        // --------------- End of particle loop ---------------------
                        time += subStepDt / 3600.0;

                        // end of particle loop
                        calcCount++;
                    }

                    // New output: print ALL current particle location to a separate file, once each hour

                    //System.out.println("Print particle locations to file " + today + " " + currentHour);
                    //IOUtils.particleLocsToFile_full(particles, "locations_" + today + "_" + currentHour + ".dat", true);

                    //IOUtils.particleLocsToFile_full(particles,currentHour,"locations_" + today + ".dat",true);
                    if (rp.recordLocations == true)
                    {
                        IOUtils.particlesToRestartFile(particles,currentHour,"locations_" + today + ".dat",true,rp);
                    }
                                        
                    // It's the end of an hour, so if particles are allowed to infect more than once, reactivate them
                    for (Particle part : particles) {
                        if (part.getSettledThisHour()==true) // previously had clause oldOutput==false here
                        {
                            // Save arrival
                            if (rp.recordArrivals==true)
                            {
                                IOUtils.arrivalToFile(part, currentIsoDate, currentHour, "arrivals_" + today + ".dat", true);
                            }
                            // Add arrival to connectivity file
                            int sourceIndex = part.getStartIndex();
                            String destSite = part.getLastArrival();
                            int destIndex = siteNames.indexOf(destSite);
                            //System.out.println("Connection made: SOURCE "+sourceIndex+" "+part.getStartID()+" DESTINATION "+destIndex+" "+destSite);
                            connectivity[sourceIndex][destIndex] += part.getDensity();
                            
                            // Reset ability to settle
                            part.setSettledThisHour(false);
                        }
                    }
                    
                    // Hourly updates to pstep arrays
                    if (rp.recordPsteps==true)
                    {
                        IOUtils.pstepsUpdater(particles, rp, pstepsMature, pstepsImmature, 3600);
                    //IOUtils.pstepsSparseUpdater(particles, rp, pstepsMature, pstepsImmature, 3600);
                    }
                    
                    // If
                    if ((stepcount+1)%rp.pstepsInterval == 0)
                    {
                        //IOUtils.writeFloatArrayToFile(pstepsImmature, "pstepsImmature_" + today + "_" + tt + "_" + stepcount + ".dat", false, false);
                        //IOUtils.writeFloatArrayToFile(pstepsMature, "pstepsMature_" + today + "_" + tt + "_" + stepcount + ".dat", false, false);
                        // Trim arrays to non-zero rows and write to file
                        //System.out.println("Trimming immature file");
                        float[][] psImmTrim = null;
                        try { psImmTrim = nonZeroRows(pstepsImmature);} catch (Exception e){}
                        //System.out.println("Trimming mature file");
                        float[][] psMatTrim = null;
                        try { psMatTrim = nonZeroRows(pstepsMature);} catch (Exception e){}
                        System.out.println("Writing psteps");
                        IOUtils.writeFloatArrayToFile(psImmTrim, "pstepsImmature_" + today + "_" + (stepcount+1) + ".dat", false, true);
                        IOUtils.writeFloatArrayToFile(psMatTrim, "pstepsMature_" + today + "_" + (stepcount+1) + ".dat", false, true);
                        
                        pstepsImmature = new float[meshes.get(0).getNElems()][habitat.size()];
                        pstepsMature = new float[meshes.get(0).getNElems()][habitat.size()];
                    }
                    
                    if ((stepcount+1)%rp.connectivityInterval == 0)
                    {
                        System.out.println("Writing connectivity");
                        IOUtils.writeFloatArrayToFile(connectivity, "connectivity_" + today + "_" + (stepcount+1) + ".dat", false, false);
                        connectivity = new float[habitat.size()][habitat.size()];
                    }
                    

                    // Clean up "dead" (666) and "exited" (66) particles
                    List<Particle> particlesToRemove = new ArrayList<>(0);
                    for (Particle part : particles)
                    {
                        if (part.getStatus()==666 || part.getStatus()==66)
                        {
                            //System.out.printf("Removing particle %d, age %f degreeDays %f status %d\n",part.getID(),part.getAge(),part.getDegreeDays(),part.getStatus());
                            particlesToRemove.add(part);
                        }
                    }
                    particles.removeAll(particlesToRemove);
                                      
                    System.out.println("Number of particles = "+particles.size());

                    printCount++;                    
                    stepcount++;
                }
                System.out.printf("\n");
                       
                //System.out.println("BACKWARDS? "+rp.backwards);
                if (rp.backwards==false)
                {
                    //System.out.println("Adjusting date to next day");
                    currentIsoDate.addDay();
                }
                else
                {
                    //System.out.println("Adjusting date to previous day");
                    currentIsoDate.takeDay();
                }
                
                // Check some particle info
//                for (Particle part : particles)
//                {
//                    System.out.println(part.getID()+" --- Age = "+part.getAge()+" DegreeDays = "+part.getDegreeDays()+" status = "+part.getStatus());
//                }
            }
            
            // Write out the final locations of the particles.
            // Note that the last hour of the last day has by now been iteracted over, and the day has been advanced
            // to the day after the simulation finished.
            // So this is the location of the particles at t=0 on the day after the last simulated day, ready to 
            // start a new run on the next day.
            IOUtils.printFileHeader(particleRestartHeader,"locationsEnd_"+currentIsoDate.getDateStr()+".dat");
            IOUtils.particlesToRestartFile(particles,0,"locationsEnd_"+currentIsoDate.getDateStr()+".dat",true,rp);
            
            
            
            
            
            System.out.printf("\nelement search counts: %d %d %d %d %d\n", searchCounts[0], searchCounts[1], searchCounts[2], searchCounts[3], searchCounts[4]);
            System.out.printf("transport distances: min = %.4e, max = %.4e\n", minMaxDistTrav[0], minMaxDistTrav[1]);
           
            executorService.shutdownNow();
        } finally {
            executorService.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Elapsed time = " + (endTime - startTime) / 1000.0);
    }
    
    /**
     * Method to create new particles. These must be appended to the existing list
     * 
     * @param habitat
     * @param meshes
     * @param rp
     * @param currentDate
     * @param currentTime
     * @param numParticlesCreated
     * @return List of the new particles to be appended to existing list
     */
    public static List<Particle> createNewParticles(List<HabitatSite> habitat, List<Mesh> meshes, 
            RunProperties rp, ISO_datestr currentDate, int currentTime, int numParticlesCreated)
    {
        //System.out.printf("In createNewParticles: nparts %d startlocsSize %d\n",rp.nparts,startlocs.length);
        List<Particle> newParts = new ArrayList<>(rp.nparts*habitat.size());
        for (int i = 0; i < rp.nparts*habitat.size(); i++)
        {
//            if (!habitat.get(i).getContainingMeshType().equalsIgnoreCase("NONE"))
//            {
                int startid = i % habitat.size();
                double xstart = habitat.get(startid).getLocation()[0];
                double ystart = habitat.get(startid).getLocation()[1];
                int meshStart = habitat.get(startid).getContainingMesh();
                int elemFVCOMStart = habitat.get(startid).getContainingFVCOMElem();
                int[] elemROMSStartU = habitat.get(startid).getContainingROMSElemU();
                int[] elemROMSStartV = habitat.get(startid).getContainingROMSElemV();
                int[] nearestROMSGridPointU = habitat.get(startid).getNearestROMSPointU();
                int[] nearestROMSGridPointV = habitat.get(startid).getNearestROMSPointV();

                Particle p = new Particle(xstart, ystart, rp.startDepth, habitat.get(startid).getID(), startid, numParticlesCreated+i, 
                        rp.mortalityRate, currentDate, currentTime, rp.coordRef, rp.species);
                p.setMesh(meshStart);
                p.setElem(elemFVCOMStart);
                p.setROMSElemU(elemROMSStartU);
                p.setROMSElemV(elemROMSStartV);
                p.setROMSnearestPointU(nearestROMSGridPointU);
                p.setROMSnearestPointV(nearestROMSGridPointV);

//                if (rp.setStartDepth == true) {
//                    p.setZ(rp.startDepth);
//                }
                newParts.add(p);
                //System.out.println(p.toString());
//            }
        }
        
        // Remove particles which were not created due to habitat site being miles from the model meshes.
        // There would be a better way of doing this - not creating the habitat site in the first place being one way!
        // OR just creating a list initially based on the number of habitat sites which didn't have "NONE" as their
        // containing mesh.
//        while(newParts.remove(null)){}
        
        return newParts;
    }
    
    /**
     * Method to handle the various cases of reading in hydrodynamic model output files.
     * The cases handled are:
     * i) Single mesh, FVCOM only. In this case, the current day is read in in its entirety,
     * plus the first hour of the next day.
     * ii) More than one mesh, hour 0-22 of the day. In this case, two hours of data are read for
     * each relevant mesh. For an FVCOM mesh, this is just two hours from the same .nc file.
     * For a ROMS mesh, this is two separate files.
     * iii) More than one mesh, hour 23 of the day. In this case, as single hour (23) is read from
     * the first file, and then record 0 from tomorrow's file is read.
     * @param meshes
     * @param currentIsoDate
     * @param tt
     * @param rp
     * @return 
     */
    public static List<HydroField> readHydroFields(List<Mesh> meshes, ISO_datestr currentIsoDate, int tt, boolean isLastDay, RunProperties rp) throws IOException
    {
        List<HydroField> hydroFields = new ArrayList<>();
        
        // 24 hr files only case - read once a day
        for (int i = 0; i < meshes.size(); i++)
        {
            if (meshes.get(i).getType().equalsIgnoreCase("FVCOM"))
            {
                //tIndex = tt;

                if (tt%rp.recordsPerFile1 == 0)
                {
                    hydroFields.clear();
                    try 
                    {
                        System.out.println("Reading file "+tt);
                        // Dima file naming format: minch2_20171229_0003.nc
                       
                        String[] varNames1 = {"u","v","salinity","temp","zeta"};
                        
                        // Normal "forwards time"
                        if (rp.backwards==false)
                        {
                            List<File> files1 = (List<File>) FileUtils.listFiles(
                                new File(rp.datadir+rp.datadirPrefix+currentIsoDate.getYear()+rp.datadirSuffix+System.getProperty("file.separator")),
                                new WildcardFileFilter(rp.location+rp.minchVersion+"_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+"*.nc"), 
                                null); 
                            ISO_datestr tomorrow = ISO_datestr.getTomorrow(currentIsoDate);
                            if (isLastDay)
                            {
                                System.out.println("** Last day - reading same hydro file twice **");
                                tomorrow = currentIsoDate;
                            }
                            List<File> files2 = (List<File>) FileUtils.listFiles(
                                new File(rp.datadir+rp.datadirPrefix+tomorrow.getYear()+rp.datadirSuffix+System.getProperty("file.separator")),
                                new WildcardFileFilter(rp.location+rp.minchVersion+"_"+tomorrow.getYear()+String.format("%02d",tomorrow.getMonth())+String.format("%02d",tomorrow.getDay())+"*.nc"), 
                                null);     
                                                    // Read both files and combine
                            hydroFields.add(new HydroField(files1.get(0).getCanonicalPath(),files2.get(0).getCanonicalPath(),varNames1,null,null,null,"FVCOM",rp.readHydroVelocityOnly));
                        } 
                        // Instead read time backwards, so need yesterday instead
                        else
                        {
                            List<File> files1 = (List<File>) FileUtils.listFiles(
                                new File(rp.datadir+rp.datadirPrefix+currentIsoDate.getYear()+rp.datadirSuffix+System.getProperty("file.separator")),
                                new WildcardFileFilter(rp.location+rp.minchVersion+"_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+"*_rev.nc"), 
                                null); 
                            ISO_datestr yesterday = ISO_datestr.getYesterday(currentIsoDate);
                            if (isLastDay)
                            {
                                System.out.println("** Last day - reading same hydro file twice **");
                                yesterday = currentIsoDate;
                            }
                            List<File> files2 = (List<File>) FileUtils.listFiles(
                                    new File(rp.datadir+rp.datadirPrefix+yesterday.getYear()+rp.datadirSuffix+System.getProperty("file.separator")),
                                    new WildcardFileFilter(rp.location+rp.minchVersion+"_"+yesterday.getYear()+String.format("%02d",yesterday.getMonth())+String.format("%02d",yesterday.getDay())+"*_rev.nc"), 
                                    null);     
                            // Read both files and combine
                            hydroFields.add(new HydroField(files1.get(0).getCanonicalPath(),files2.get(0).getCanonicalPath(),varNames1,null,null,null,"FVCOM",rp.readHydroVelocityOnly));
                        }
                        

                    }
                    catch (Exception e)
                    {
                        System.out.println("Hydro file not found, check PROPERTIES: datadir, datadirPrefix, datadirSuffix, location, minchVersion");
                        if (rp.backwards==false)
                        {
                            System.err.println("Requested file: "+rp.datadir+rp.datadirPrefix+currentIsoDate.getYear()+rp.datadirSuffix+System.getProperty("file.separator")
                                +rp.location+rp.minchVersion+"_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+"*.nc");
                        }
                        else
                        {
                            System.err.println("Requested file: "+rp.datadir+rp.datadirPrefix+currentIsoDate.getYear()+rp.datadirSuffix+System.getProperty("file.separator")
                                +rp.location+rp.minchVersion+"_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+"*_rev.nc");
                        }
                        System.exit(1);
                    }
                }
            }
            else if (meshes.get(i).getType().equalsIgnoreCase("ROMS_TRI"))
            {
                String filename1 = rp.datadir2+rp.datadir2Prefix+currentIsoDate.getYear()+rp.datadir2Suffix+System.getProperty("file.separator")
                +"NEATL_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+".nc";
                String filename2 = rp.datadir2+rp.datadir2Prefix+currentIsoDate.getYear()+rp.datadir2Suffix+System.getProperty("file.separator")
                +"NEATL_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+".nc";
                String[] varNames1 = {"u","v","","",""};
                // Read both files and combine
                hydroFields.add(new HydroField(filename1,filename2,varNames1,null,null,null,"ROMS_TRI",rp.readHydroVelocityOnly));   
            }
            
        }
//        // Reading two hours at a time from all the different models
//        else
//        { 
//            // i) The case where it is NOT the last hour of the day
//            if ((tt-23)%24 != 0)
//            {
//                hydroFields.clear();
//                for (int i = 0; i < meshes.size(); i++)
//                {
//                    if (meshes.get(i).getType().equalsIgnoreCase("FVCOM"))
//                    {
//                        // FVCOM files don't have a guaranteed ending, so need to use the Wildcard file filter
//                        List<File> f = (List<File>) FileUtils.listFiles(
//                            new File(rp.datadir+rp.datadirPrefix+currentIsoDate.getYear()+rp.datadirSuffix+System.getProperty("file.separator")),
//                            new WildcardFileFilter(rp.location+rp.minchVersion+"_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+"*.nc"), 
//                            null); 
//                        String[] varNames = new String[]{"u","v","salinity","temp","zeta"};
//                        int[] origin = new int[]{tt,0,0};
//                        int[] shape = new int[]{2,meshes.get(i).getSiglay().length,meshes.get(i).getUvnode()[1].length}; // U/V are stored on element centroids in FVCOM
//                        int[] shapeST = new int[]{2,meshes.get(i).getSiglay().length,meshes.get(i).getNodexy()[1].length}; // S/T are stored on element corners in FVCOM
//                        hydroFields.add(new HydroField(f.get(0).getCanonicalPath(),varNames,origin,shape,shapeST,"FVCOM",rp.readHydroVelocityOnly));
//                    }
//                    else if (meshes.get(i).getType().equalsIgnoreCase("ROMS"))
//                    {
//                        // ROMS files DO have a guaranteed name format, so just use a string for the name
//                        String filename1 = rp.datadir2+rp.datadir2Prefix+currentIsoDate.getYear()+rp.datadir2Suffix+System.getProperty("file.separator")
//                            +"NEATL_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+String.format("%02d",tt)+".nc";
//                        String filename2 = rp.datadir2+rp.datadir2Prefix+currentIsoDate.getYear()+rp.datadir2Suffix+System.getProperty("file.separator")
//                            +"NEATL_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+String.format("%02d",tt+1)+".nc";
//
//                        String[] varNames = new String[]{"ubar","vbar","","","zeta"};
//
//                        int[][] r = meshes.get(i).getRange();
//                        int[] origin = new int[]{0,r[0][0],r[1][0]};
//                        int[] shape = new int[]{1,r[0][1]-r[0][0],r[1][1]-r[1][0]};
//                        int[] shapeST = new int[]{1,r[0][1]-r[0][0],r[1][1]-r[1][0]}; // S/T are same SHAPE as U/V in ROMS, just on a different grid (lon_psi, lat_psi)
//                        hydroFields.add(new HydroField(filename1,filename2,varNames,origin,shape,shapeST,"ROMS",rp.readHydroVelocityOnly));
//                    }
//                }
//            } 
//            // The case that it IS the last hour of the day
//            else
//            {
//                hydroFields.clear();
//                for (int i = 0; i < meshes.size(); i++)
//                {
//                    if (meshes.get(i).getType().equalsIgnoreCase("FVCOM"))
//                    {
//                        // FVCOM files don't have a guaranteed ending, so need to use the Wildcard file filter
//                        List<File> f1 = (List<File>) FileUtils.listFiles(
//                            new File(rp.datadir+rp.datadirPrefix+currentIsoDate.getYear()+rp.datadirSuffix+System.getProperty("file.separator")),
//                            new WildcardFileFilter(rp.location+rp.minchVersion+"_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+"*.nc"), 
//                            null);
//
//                        ISO_datestr tomorrow = ISO_datestr.getTomorrow(currentIsoDate);
//
//                        List<File> f2 = (List<File>) FileUtils.listFiles(
//                            new File(rp.datadir+rp.datadirPrefix+tomorrow.getYear()+rp.datadirSuffix+System.getProperty("file.separator")),
//                            new WildcardFileFilter(rp.location+rp.minchVersion+"_"+tomorrow.getYear()+String.format("%02d",tomorrow.getMonth())+String.format("%02d",tomorrow.getDay())+"*.nc"), 
//                            null);
//
//                        String[] varNames = new String[]{"u","v","salinity","temp","zeta"};
//                        int[] origin = new int[]{tt,0,0};
//                        int[] shape = new int[]{1,meshes.get(i).getSiglay().length,meshes.get(i).getUvnode()[1].length};
//                        int[] shapeST = new int[]{1,meshes.get(i).getSiglay().length,meshes.get(i).getNodexy()[1].length};
//                        hydroFields.add(new HydroField(f1.get(0).getCanonicalPath(),f2.get(0).getCanonicalPath(),varNames,origin,shape,shapeST,"FVCOM",rp.readHydroVelocityOnly));
//                    }
//                    else if (meshes.get(i).getType().equalsIgnoreCase("ROMS"))
//                    {
//                        // ROMS files DO have a guaranteed name format, so just use a string for the name
//                        String filename1 = rp.datadir2+rp.datadir2Prefix+currentIsoDate.getYear()+rp.datadir2Suffix+System.getProperty("file.separator")
//                            +"NEATL_"+currentIsoDate.getYear()+String.format("%02d",currentIsoDate.getMonth())+String.format("%02d",currentIsoDate.getDay())+"23.nc";
//
//                        ISO_datestr tomorrow = ISO_datestr.getTomorrow(currentIsoDate);
//
//                        String filename2 = rp.datadir2+rp.datadir2Prefix+tomorrow.getYear()+rp.datadir2Suffix+System.getProperty("file.separator")
//                            +"NEATL_"+tomorrow.getYear()+String.format("%02d",tomorrow.getMonth())+String.format("%02d",tomorrow.getDay())+"00.nc";
//
//                        String[] varNames = new String[]{"ubar","vbar","","","zeta"};
//
//                        int[][] r = meshes.get(i).getRange();
//                        int[] origin = new int[]{0,r[0][0],r[1][0]};
//                        int[] shape = new int[]{1,r[0][1]-r[0][0],r[1][1]-r[1][0]};
//                        int[] shapeST = new int[]{1,r[0][1]-r[0][0],r[1][1]-r[1][0]}; // S/T are same SHAPE as U/V in ROMS, just on a different grid
//                        hydroFields.add(new HydroField(filename1,filename2,varNames,origin,shape,shapeST,"ROMS",rp.readHydroVelocityOnly));
//                    }
//
//                }
//            }
//        }

        return hydroFields;
    }
    
    
    
    
    
    

    /**
     * Count the number of particles in different states (free, viable, settled,
     * exited domain)
     *
     * @param parts
     * @return
     */
    public static int[] particleCounts(List<Particle> parts) {
        int freeViableSettleExit[] = new int[4];
        // Add count 1 for each particle that satisfies this list of conditions
        // Lines below are equivalent to:
        //if (p.getFree()) {
        //    freeViableSettleExit[0] += 1;
        //} 
        for (Particle p : parts) {
            freeViableSettleExit[0] += p.getFree() ? 1 : 0;
            freeViableSettleExit[1] += p.getViable() ? 1 : 0;
            freeViableSettleExit[2] += p.getArrived() ? 1 : 0;
            freeViableSettleExit[3] += p.getBoundaryExit() ? 1 : 0;
        }
        return freeViableSettleExit;
    }
    
    
    
    
    
    // calculate a connectivity matrix detailing the 
    public static double[][] connectFromParticleArrivals(List<Particle> particles, int nStartLocs, int npartsPerSite)
    {
        double[][] connectMatrix = new double[nStartLocs][nStartLocs];
        for (Particle part : particles)
        {
            for (Arrival arrival: part.getArrivals())
            {
                connectMatrix[arrival.getSourceLocation()][arrival.getArrivalLocation()] += arrival.getArrivalDensity()/npartsPerSite;
            }
        }
        return connectMatrix;
    }

    
    public static int[] nonZeroVals(int[] A) {
        int count = 0;
        for (int i = 0; i < A.length; i++) {
            if (A[i] != 0) {
                count++;
            }
        }
        int[] temp = new int[count];
        int p = 0;
        for (int i = 0; i < A.length; i++) {
            if (A[i] != 0) {
                temp[p++] = A[i];
            }
        }
        return temp;
    }
    
    public static float[][] nonZeroRows(float[][] A) {
        int count = 0;
        List<Integer> list = new ArrayList<Integer>();
        
        for (int i = 0; i < A.length; i++) {
            // Check whether ANY of the elements on this row !=0
            for (int j = 0; j < A[0].length; j++){
                if (A[i][j] > 0) {
                    list.add(i);
                    count++;
                    break;
                }
            }
        }
        System.out.println("count "+count);
        float[][] temp = null;
        if (count > 0)
        {
            temp = new float[count][A[0].length+1];
            System.out.println("temp size = "+temp.length+" "+temp[0].length);
            //System.out.println("A size = "+A.length+" "+A[0].length);
            int p = 0;
            for (int i = 0; i < list.size(); i++) {
                int row = list.get(i);
                temp[p][0] = row;
                for (int j = 0; j < A[0].length; j++){
                    //System.out.println(A[row][j]);
                    if (A[row][j] > 0) {
                        temp[p][j+1] = A[row][j];
                    }
                }
                p++;
            }
        }
        return temp;
    }
    
    

    public static void memTest() {
        long heapSize = Runtime.getRuntime().totalMemory();
        System.out.println("Total heap memory " + heapSize);
        long heapFreeSize = Runtime.getRuntime().freeMemory();
        System.out.println("Free heap memory " + heapFreeSize);

    }

    public void setupOutput() {
    }

    public void writeOutput() {
    }
}
