package cz.wa2.entity;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;

@Entity
@Table(name = "Errors")
public class Error {

	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;
	
	@Column(name = "message", nullable = true)
	private String message;
	
	@ManyToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL)
	@JoinColumn(name = "user_id")
	private User user;
	
	@Column(name = "canceled")
	private boolean canceled;
	
	@Column(name = "resolved")
	private boolean resolved;
	
	@Column(name = "screenshot", nullable = true)
	private String screenshot;
	
	@ManyToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL)
	@JoinColumn(name = "page_id")
	private Page page;
	
	@Column(name = "comment", nullable = true)
	private String comment;
	
	@Column(name = "report_url_time", nullable = true, columnDefinition="DATETIME")
	@Temporal(TemporalType.TIMESTAMP)
	private Date reportUrlDate;
	
	@Column(name = "report_url", nullable = true)
	private String reportUrl;
	
	@Column(name = "screen_url_time", nullable = true, columnDefinition="DATETIME")
	@Temporal(TemporalType.TIMESTAMP)
	private Date screenUrlDate;
	
	@Column(name = "screen_url", nullable = true)
	private String screenUrl;
	
	@Version
	@Column(name = "optlock")
	private Integer optlock;
	
	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name="Candidates", joinColumns=@JoinColumn(name="error_id"), inverseJoinColumns=@JoinColumn(name="user_id"))
	private List<User> candidates;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public boolean isCanceled() {
		return canceled;
	}

	public void setCanceled(boolean canceled) {
		this.canceled = canceled;
	}

	public boolean isResolved() {
		return resolved;
	}

	public void setResolved(boolean resolved) {
		this.resolved = resolved;
	}

	public String getScreenshot() {
		return screenshot;
	}

	public void setScreenshot(String screenshot) {
		this.screenshot = screenshot;
	}

	public Page getPage() {
		return page;
	}

	public void setPage(Page page) {
		this.page = page;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public Date getReportUrlDate() {
		return reportUrlDate;
	}

	public void setReportUrlDate(Date reportUrlDate) {
		this.reportUrlDate = reportUrlDate;
	}

	public String getReportUrl() {
		return reportUrl;
	}

	public void setReportUrl(String reportUrl) {
		this.reportUrl = reportUrl;
	}

	public Date getScreenUrlDate() {
		return screenUrlDate;
	}

	public void setScreenUrlDate(Date screenUrlDate) {
		this.screenUrlDate = screenUrlDate;
	}

	public String getScreenUrl() {
		return screenUrl;
	}

	public void setScreenUrl(String screenUrl) {
		this.screenUrl = screenUrl;
	}

	public Integer getOptlock() {
		return optlock;
	}

	public void setOptlock(Integer optlock) {
		this.optlock = optlock;
	}

	public List<User> getCandidates() {
		return candidates;
	}

	public void setCandidates(List<User> candidates) {
		this.candidates = candidates;
	}
	
	

}
