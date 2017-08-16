package repository;

import java.io.*;
import java.util.*;
import play.Logger;

/**
 * sbt "runMain repository.C5Solver GRAPH..."
 */
public class C5Solver {
    long[] nodes;
    long[][] edges;
    Map<String, BitSet> vprops = new TreeMap<>();
    Map<String, BitSet> eprops = new TreeMap<>();
    Map<Long, Integer> lut = new HashMap<>();
    int[] rank, parent;
        
    public C5Solver () {
    }

    int find (int n) {
        int p = parent[n];
        while (p != n) {
            n = p;
            p = parent[p];
        }
        return p;
    }

    void union (int i, int j) {
        int pi = find (i);
        int pj = find (j);
        if (pi == pj)
            ;
        else if (rank[pi] < rank[pj])
            parent[pi] = pj;
        else if (rank[pi] > rank[pj])
            parent[pj] = pi;
        else {
            parent[pj] = pi;
            ++rank[pi];
        }
    }

    public void load (InputStream is) throws IOException {
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        String line = br.readLine();
        String[] toks = line.split("\\s");
        if (toks.length != 2)
            throw new IllegalArgumentException ("Not a valid graph format");
        int nv = Integer.parseInt(toks[0]);
        int ne = Integer.parseInt(toks[1]);
        
        Logger.debug("Loading graph "+nv+" nodes "+ne+" edges...");
        vprops.clear();
        nodes = new long[nv];
        rank = new int[nv];
        parent = new int[nv];
        for (int i = 0; i < nv; ++i) {
            line = br.readLine();
            if (line == null)
                throw new IllegalArgumentException ("Premature end of file");
            toks = line.split("\\s");
            nodes[i] = Long.parseLong(toks[0]);
            for (int j = 1; j < toks.length; ++j) {
                BitSet bs = vprops.get(toks[j]);
                if (bs == null)
                    vprops.put(toks[j], bs = new BitSet (nv));
                bs.set(i);
            }
            lut.put(nodes[i], i);
            parent[i] = i;
        }

        eprops.clear();
        edges = new long[nv][nv];
        for (int i = 0; i < ne; ++i) {
            line = br.readLine();
            if (line == null)
                throw new IllegalArgumentException ("Premature end of file");
            toks = line.split("\\s");
            long a = Long.parseLong(toks[0]);
            long b = Long.parseLong(toks[1]);
            Integer na = lut.get(a);
            Integer nb = lut.get(b);
            if (na == null) {
                Logger.warn("Unknown node "+a+" in edge ("+a+","+b+")");
            }
            else if (nb == null) {
                Logger.warn("Unknown node "+b+" in edge ("+a+","+b+")");
            }
            else {
                union (na, nb);
                for (int j = 2; j < toks.length; ++j) {
                    BitSet bs = eprops.get(toks[j]);
                    if (bs == null)
                        eprops.put(toks[j], bs = new BitSet (ne));
                    bs.set(i);
                }
                edges[na][nb] = edges[nb][na] = i;
            }
        }

        BitSet cc = new BitSet ();
        for (int i = 0; i < nv; ++i)
            cc.set(find (i));

        Logger.debug("Graph loaded; "+vprops.size()+" node properties and "
                     +eprops.size()+" edge properties!");
        Logger.debug("Graph has "+cc.cardinality()+" connected componented!");
    }

    public static void main (String[] argv) throws Exception {
        if (argv.length == 0) {
            System.err.println("Usage: repository.C5Solver GRAPH");
            System.exit(1);
        }
        
        C5Solver c5 = new C5Solver ();
        for (String a : argv) {
            FileInputStream fis = new FileInputStream (a);
            Logger.debug("Loading file "+a);
            c5.load(fis);
            fis.close();
        }
    }
}


    
