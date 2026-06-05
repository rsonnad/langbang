# LangBang.org Site Worker

This directory is the source of truth for the live `https://langbang.org` site
Worker. The legacy `/Users/rahulio/Documents/CodingProjects/langbang` checkout
is deprecated and should not be used for site deploys.

Deploy the site Worker from the LangBangML checkout:

```bash
scripts/deploy-langbang-org-site.sh
```

`scripts/publish-langbangml-builds.sh` also runs this deploy helper after
publishing APK manifests unless `--no-site-deploy` is passed.

