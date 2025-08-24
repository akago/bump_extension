package miner;

import org.kohsuke.github.*;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The PullRequestFilters class contains predicates over GitHub repositories
 * that can be used to filter for pull requests having certain properties.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class PullRequestFilters {

    private PullRequestFilters() { /* Nothing to see here... */ }

    private static final Logger log = LoggerFactory.getLogger(PullRequestFilters.class);

    private static final Pattern POM_XML_CHANGE = Pattern.compile("^[+]{3}.*pom.xml$", Pattern.MULTILINE);
    private static final Pattern DEPENDENCY_VERSION_CHANGE =
            Pattern.compile("<dependency>(.*^[+-]\\s*<version>.+</version>.*){2}</dependency>",
                       Pattern.DOTALL | Pattern.MULTILINE);

    /**
     * Check whether a given pull request fulfills all of these properties:
     * <ul>
     *     <li>It changes only one line.</li>
     *     <li>The change is made to a pom.xml file.</li>
     *     <li>The change is to the version number in a version tag.</li>
     * </ul>
     */
    public static final Predicate<GHPullRequest> changesOnlyDependencyVersionInPomXML = pr -> {
        int attempts = 0;
        while (attempts < 3) {
            try {
                if (pr.getChangedFiles() != 1)
                    return false;
                if (pr.getAdditions() != 1 || pr.getDeletions() != 1)
                    return false;

                String patch = GitPatchCache.get(pr).orElse("");
                if (POM_XML_CHANGE.matcher(patch).find() && DEPENDENCY_VERSION_CHANGE.matcher(patch).find()) {
                    return true;
                } else {
                    // If we don't match the predicate, the pull request will get filtered out,
                    // and we can remove it from the cache.
                    GitPatchCache.remove(pr);
                    return false;
                }
            } catch (HttpException e) {
                if (e.getResponseCode() >= 500) {
                    attempts++;
                    log.warn("Server error {} while inspecting PR {}, attempt {}/3", e.getResponseCode(), pr.getHtmlUrl(), attempts);
                    try {
                        Thread.sleep(1000L * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        log.warn("Skipping PR {} due to repeated server errors", pr.getHtmlUrl());
        return false;
    };

    /**
     * Checks whether this pull request breaks a GitHub Action workflow in the associated repository.
     */
    public static final Predicate<GHPullRequest> breaksBuild = pr -> {
        int attempts = 0;
        while (attempts < 3) {
            GHWorkflowRunQueryBuilder query = pr.getRepository().queryWorkflowRuns()
                    .branch(pr.getHead().getRef())
                    .event(GHEvent.PULL_REQUEST)
                    .status(GHWorkflowRun.Status.COMPLETED)
                    .conclusion(GHWorkflowRun.Conclusion.FAILURE);
            try {
                // The GitHub REST API allows us to query for the head sha, but this is not currently supported
                // by org.kohsuke.github query builder. To ensure that the run actually failed for this specific
                // PR head, we have to verify it after the search.
                return query.list().toList().stream()
                        .anyMatch(run -> run.getHeadSha().equals(pr.getHead().getSha()));
            } catch (HttpException e) {
                if (e.getResponseCode() >= 500) {
                    attempts++;
                    log.warn("Server error {} while checking workflows for PR {}, attempt {}/3", e.getResponseCode(), pr.getHtmlUrl(), attempts);
                    try {
                        Thread.sleep(1000L * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        log.warn("Skipping build check for PR {} due to repeated server errors", pr.getHtmlUrl());
        return false;
    };

    /**
     * Checks whether a pull request was created before the given date.
     * @param cutoffDate The point in time which the PR must have been created before.
     * @return a {@link java.util.function.Predicate} over {@link org.kohsuke.github.GHPullRequest}s returning
     *         true if a PR was created before the given date, false otherwise.
     */
    public static Predicate<GHPullRequest> createdBefore(Date cutoffDate) {
        return pr -> {
            try {
                return pr.getCreatedAt().before(cutoffDate);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
