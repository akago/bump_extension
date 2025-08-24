#!/bin/bash
#SBATCH -J  BUminer
#SBATCH -p  mcs.default.q         # 7-day CPU queue
#SBATCH --nodes=1              # 1 node
#SBATCH --cpus-per-task=3        # 4 logical cores for the JVM
#SBATCH --mem=8G                 # enough head-room for the heap + I/O buffers
#SBATCH --time=7-00:00:00         # max allowed in this partition
#SBATCH --output=BUminer-%j.log   # <JobName>-<JobID>.log
#SBATCH --open-mode=append        # keep handle open, append new lines
# ──────────────────────────────────────────────────────────────────
module purge
module load Java/17.0.6          # or whatever JDK you need

# Force line-buffered stdout so `tail -f` looks “live”
stdbuf -oL \
java -jar target/BreakingUpdateMiner.jar \
     mine -a ./.env -o ./new_data -r found_repositories.json