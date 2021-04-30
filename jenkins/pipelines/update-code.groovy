// make new cache folder named with date
// copy all repo from current cache folder
// pull to update repo in new cache folder
// tar repo and touch md5sum file
// update softlink

node("ci-admin-utils") {
    Date date = new Date()
    String datePart = date.format("yyyyMMdd")
    String timePart = date.format("HH:mm:ss")

    println "datePart : " + datePart + "\ttimePart : " + timePart

    def repoPathMap = [:]

    stage('Collect info') {
        container("golang") {
            sh "ls -ld /nfs/cache/git"
            dir("/nfs/cache/git") {
                def files = findFiles()
                files.each { f ->
                    if (f.directory) {
                        echo "This is dirctory: ${f.name}"
                        dir(f.name) {
                            if (fileExists("./.git/config")) {
                                echo "This is a valid git repo"
                                def workspace = pwd()
                                repoPathMap[f.name] = workspace
                            } else {
                                echo "This is invalid git repo. Skip..."
                            }
                        }
                        dir("${f.name}@tmp") {
                            deleteDir()
                        }
                    }
                }
            }
        }
    }

    stage('Copy') {
        container('golang') {
            res = sh(script: "test -d /nfs/cache/git-archive/${datePart} && echo '1' || echo '0' ", returnStdout: true).trim()
            if (res == '1') {
                echo "dir already exist, quit job in case of delete current using code cache dir"
                error("Build failed because of dir exist now: /nfs/cache/git-archive/${datePart}")
            }
            dir("/nfs/cache/git-archive/${datePart}") {
                repoPathMap.each { entry ->
                    sh """
                        cp -R ${entry.value} ./ && [ -d ${entry.key}/.git ]
                     """
                    dir(entry.key) {
                        sh """
                            pwd
                            rm -f ./git/index.lock &&  git clean -fnd && git checkout . && git pull --all
                        """
                    }
                    dir("${entry.key}@tmp") {
                        deleteDir()
                    }
//           sh """
//             tar -C ${entry.key} -czf src-${entry.key}.tar.gz .
//             md5sum src-${entry.key}.tar.gz > src-${entry.key}.tar.gz.md5sum
//           """
                    sh """
                        tar -czf src-${entry.key}.tar.gz ${entry.key}
                        md5sum src-${entry.key}.tar.gz > src-${entry.key}.tar.gz.md5sum
                    """
                }
            }
            dir("/nfs/cache/git-archive/${datePart}@tmp") {
                deleteDir()
            }
        }
    }

    stage('Update softlink') {
        // sh " ln -sfn /nfs/cache/git-archive/${datePart}  /nfs/cache/git-test"
        // source dir absolute path must be same between nfs host server and agent pod
        // agent pod nfs volume mount must set "Server path" and "Mount path" to same value
        sh " ln -sfn /mnt/ci.pingcap.net-nfs/git-archive/${datePart}  /nfs/cache/git"
    }

}