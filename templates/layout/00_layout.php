<!DOCTYPE html>
<!--[if lt IE 7]>       <html class="no-js ie6 oldie" lang="en"> <![endif]-->
<!--[if IE 7]>          <html class="no-js ie7 oldie" lang="en"> <![endif]-->
<!--[if IE 8]>          <html class="no-js ie8 oldie" lang="en"> <![endif]-->
<!--[if gt IE 8]><!-->  <html class="no-js" lang="en"> <!--<![endif]-->
<head>
    <title><?= $page['title']; ?> <?php if ($page['title'] != $params['title']) {
    echo '- ' . $params['title'];
} ?></title>
    <meta name="description" content="<?= $params['tagline']; ?>" />
    <meta name="author" content="<?= $params['author']; ?>">
    <meta charset="UTF-8">
    <link rel="icon" href="<?= $params['theme']['favicon']; ?>" type="image/x-icon">
    <!-- Mobile -->
    <meta name="apple-mobile-web-app-capable" content="yes" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <!-- Font -->
    <?php foreach ($params['theme']['fonts'] as $font) {
    echo "<link href='$font' rel='stylesheet' type='text/css'>";
} ?>

    <!-- CSS -->
    <?php foreach ($params['theme']['css'] as $css) {
    echo "<link href='$css' rel='stylesheet' type='text/css'>";
} ?>

    <?php if ($params['html']['search']) {
    ?>
        <!-- Tipue Search -->
        <link href="<?= $base_url; ?>tipuesearch/tipuesearch.css" rel="stylesheet">
    <?php

} ?>

    <!--[if lt IE 9]>
    <script src="http://html5shiv.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->
</head>
<body class="<?= $params['html']['float'] ? 'with-float' : ''; ?>">
    <?= $this->section('content'); ?>

    <?php
    if ($params['html']['google_analytics']) {
        $this->insert('theme::partials/google_analytics', ['analytics' => $params['html']['google_analytics'], 'host' => array_key_exists('host', $params) ? $params['host'] : '']);
    }
    if ($params['html']['piwik_analytics']) {
        $this->insert('theme::partials/piwik_analytics', ['url' => $params['html']['piwik_analytics'], 'id' => $params['html']['piwik_analytics_id']]);
    }
    ?>

    <script src="<?= $base_url; ?>themes/daux/js/jquery-1.12.4.min.js"></script>
    <script src="<?= $base_url; ?>themes/daux/js/highlight-9.12.0.min.js"></script>
    <script type="text/javascript" src="<?= $base_url; ?>themes/daux/js/anchor-4.1.0.min.js"></script>
    <script src="<?= $base_url; ?>themes/daux/js/daux.js"></script>
    <script type="text/javascript">
         hljs.initHighlightingOnLoad();
         anchors.add();
    </script>
</body>
</html>
