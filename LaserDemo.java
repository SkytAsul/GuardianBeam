

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import fr.skytasul.guardianbeam.Laser.GuardianLaser;

public class LaserDemo extends JavaPlugin implements Listener{

	private Map<Player, LaserRunnable> lasers =new HashMap<>();
	
	@Override
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable(){
		lasers.forEach((p, run) -> run.laser.stop());
	}
	
  // Command needed in plugin.yml
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if (!(sender instanceof Player)) return true;
		Player p = (Player) sender;
		
		if (lasers.containsKey(p)){
			lasers.get(p).cancel();
		}else {
			try{
				lasers.put(p, new LaserRunnable(p));
				lasers.get(p).runTaskTimer(this, 5, 1);
			}catch (ReflectiveOperationException e){
				e.printStackTrace();
			}
		}
		return true;
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e){
		if (e.getAction() != Action.LEFT_CLICK_AIR) return;
		Player p = e.getPlayer();
		if (!lasers.containsKey(p)) return;
		
		LaserRunnable run = lasers.get(p);
		if (run.loading != LaserRunnable.LOADING_TIME) return;
		p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2, 1);
		run.loading = 0;

		for (Block blc : p.getLineOfSight(null, LaserRunnable.RANGE / 2)){
			for (Entity en : p.getWorld().getNearbyEntities(blc.getLocation(), 1, 1, 1)){
				if (en instanceof Player) continue;
				if (en instanceof LivingEntity){
					((LivingEntity) en).damage(20, p);
					en.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, en.getLocation(), 4, 1, 1, 1, 0.1);
				}
			}
		}
		p.getWorld().spawnParticle(Particle.SMOKE_LARGE, run.laser.getEnd(), 5);
		try {
			run.laser.callColorChange();
		}catch (ReflectiveOperationException e1) {
			e1.printStackTrace();
		}
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent e){
		if (lasers.containsKey(e.getPlayer())){
			lasers.get(e.getPlayer()).cancel();
		}
	}
	
	@EventHandler
	public void onDeath(PlayerDeathEvent e){
		if (lasers.containsKey(e.getEntity())){
			lasers.get(e.getEntity()).cancel();
		}
	}
	
	public class LaserRunnable extends BukkitRunnable{
		public static final byte LOADING_TIME = 30;
		public static final byte RANGE = 10;
		
		private final GuardianLaser laser;
		private final Player p;
		
		public byte loading = 0;
		
		public LaserRunnable(Player p) throws ReflectiveOperationException{
			this.p = p;
			this.laser = new GuardianLaser(p.getLocation(), p.getLocation().add(0, 1, 0), -1, 50);
			this.laser.start(LaserDemo.this);
		}
		
		@Override
		public void run(){
			if (loading != LOADING_TIME){
				loading++;
				p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, loading == LOADING_TIME ? 1.5f : 0.2f);
			}
			try{	
				laser.moveStart(p.getLocation().add(0, 0.8, 0));
				laser.moveEnd(p.getLocation().add(0, 1.2, 0).add(p.getLocation().getDirection().multiply(loading == LOADING_TIME ? RANGE : loading / (LOADING_TIME / RANGE * 1.3))));
			}catch (ReflectiveOperationException e){
				e.printStackTrace();
			}
		}
    
		@Override
		public synchronized void cancel() throws IllegalStateException{
			laser.stop();
			lasers.remove(p);
			super.cancel();
		}
	}
  
}
