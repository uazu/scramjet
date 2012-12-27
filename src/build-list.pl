#!/usr/bin/perl -w

sub search {
    my $dir = shift;
    return if (-f "$dir/_NOBUILD_");
    
    die "Can't open directory: $dir" unless opendir DIR, "$dir";
    my @list = sort readdir DIR;
    die "Can't close directory: $dir" unless closedir DIR;

    for (@list) {
        next if $_ eq '.' || $_ eq '..';
        my $fnam = "$dir/$_";
        if (/\.java$/) {
            print "$fnam\n";
        } elsif (-d $fnam) {
            search($fnam);
        }
    }
}

search "net";

