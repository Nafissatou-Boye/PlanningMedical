package sn.mediplan.web;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adresses auxquelles MediPlan est joignable depuis un autre appareil du même réseau (Wi-Fi) :
 * sert à afficher l'adresse et le QR code « Ouvrir sur mon téléphone ».
 */
@RestController
public class AccesReseauController {

    public record Acces(int port, List<String> adresses) {
    }

    private final Environment environnement;

    public AccesReseauController(Environment environnement) {
        this.environnement = environnement;
    }

    @GetMapping("/api/configuration/acces")
    public Acces acces() {
        int port = environnement.getProperty("local.server.port", Integer.class,
                environnement.getProperty("server.port", Integer.class, 8080));
        List<String> adresses = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String nom = (ni.getDisplayName() + " " + ni.getName()).toLowerCase(Locale.ROOT);
                // On écarte les cartes virtuelles (VirtualBox, VMware, Hyper-V, WSL, VPN...)
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()
                        || nom.matches(".*(virtual|vmware|vbox|hyper-v|vethernet|wsl|docker|tap|tun|bluetooth).*")) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        adresses.add("http://" + a.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (Exception e) {
            // pas de réseau : liste vide
        }
        return new Acces(port, adresses);
    }
}
