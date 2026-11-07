# React single-page app bundled into the WAR

The UI must be vivid and animated (view transitions, slide builds, animated charts), which server-rendered Thymeleaf with htmx does poorly. The UI is a React 18 + Vite app using Motion and Apache ECharts, built at package time and served by Tomcat from the same WAR as the Spring REST API under `/api`. Node is needed on the build machine only, never on the server. Only the latest Edge and Chrome are supported.
